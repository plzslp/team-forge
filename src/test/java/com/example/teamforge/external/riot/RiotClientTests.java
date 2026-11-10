package com.example.teamforge.external.riot;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RiotClientTests {
    @Test
    void usesRegionalAccountThenPlatformLeagueEndpointAndOnlySoloQueue() {
        // given: 외부 통신 경계에서 URL·토큰 헤더와 실제 JSON 디코딩을 확인한다.
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            assertEquals("test-key", request.headers().getFirst("X-Riot-Token"));
            if (calls.getAndIncrement() == 0) {
                assertEquals("asia.api.riotgames.com", request.url().getHost());
                assertTrue(request.url().getRawPath().contains("%20"));
                return json("{\"puuid\":\"player-puuid\",\"gameName\":\"ignored\"}");
            }
            assertEquals("kr.api.riotgames.com", request.url().getHost());
            assertEquals("/lol/league/v4/entries/by-puuid/player-puuid", request.url().getPath());
            return json("[{\"queueType\":\"RANKED_FLEX_SR\",\"tier\":\"CHALLENGER\",\"rank\":\"I\",\"leaguePoints\":500},"
                    + "{\"queueType\":\"RANKED_SOLO_5x5\",\"tier\":\"GOLD\",\"rank\":\"II\",\"leaguePoints\":45,\"wins\":10}]");
        });

        // when
        RiotRank rank = new RiotClient(builder, "test-key").findSoloRank("한 글#KR1");

        // then
        assertEquals("player-puuid", rank.puuid());
        assertEquals(1445, rank.score());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @CsvSource({"IRON,IV,0,0", "BRONZE,III,10,510", "SILVER,I,99,1199",
            "GOLD,II,45,1445", "PLATINUM,IV,0,1600", "EMERALD,I,80,2380",
            "DIAMOND,IV,20,2420", "MASTER,I,100,2900", "GRANDMASTER,I,100,3100",
            "CHALLENGER,I,100,3300"})
    void convertsRankUsingAgreedFormula(String tier, String division, int lp, int expected) {
        // given
        RiotRank rank = new RiotRank("puuid", tier, division, lp);
        // when / then
        assertEquals(expected, rank.score());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"queueType\":\"RANKED_FLEX_SR\",\"tier\":\"GOLD\",\"rank\":\"I\",\"leaguePoints\":50}]"})
    void successfulResponseWithoutSoloQueueIsUnranked(String body) {
        // given
        RiotClient client = clientReturningLeague(body);
        // when
        RiotRank rank = client.findSoloRank("player#KR1");
        // then
        assertNull(rank.tier());
        assertNull(rank.leaguePoints());
        assertEquals(0, rank.score());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[null]", "[{}]",
            "[{\"queueType\":\"RANKED_SOLO_5x5\",\"tier\":\"UNKNOWN\",\"rank\":\"I\",\"leaguePoints\":1}]",
            "[{\"queueType\":\"RANKED_SOLO_5x5\",\"tier\":\"GOLD\",\"rank\":\"I\",\"leaguePoints\":-1}]"})
    void rejectsMalformedLeagueResponsesInsteadOfResettingToUnranked(String body) {
        // given
        RiotClient client = clientReturningLeague(body);
        // when / then
        assertError(ErrorCode.RIOT_UNAVAILABLE, () -> client.findSoloRank("player#KR1"));
    }

    @ParameterizedTest
    @CsvSource({"401,RIOT_AUTHENTICATION_FAILED", "403,RIOT_AUTHENTICATION_FAILED",
            "404,RIOT_ACCOUNT_NOT_FOUND", "500,RIOT_UNAVAILABLE", "302,RIOT_UNAVAILABLE"})
    void mapsAccountErrorsWithoutExposingExternalBody(int status, ErrorCode expected) {
        // given
        RiotClient client = new RiotClient(WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).body("private upstream body").build())), "test-key");
        // when / then
        assertError(expected, () -> client.findSoloRank("player#KR1"));
    }

    @Test
    void pausesFurtherRequestsAfterRateLimitWithoutRetrying() {
        // given
        AtomicInteger calls = new AtomicInteger();
        RiotClient client = new RiotClient(WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120").build());
        }), "test-key");
        // when / then
        assertError(ErrorCode.RIOT_RATE_LIMITED, () -> client.findSoloRank("player#KR1"));
        assertError(ErrorCode.RIOT_RATE_LIMITED, () -> client.findSoloRank("other#KR1"));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"player", "#KR1", "player#", "a#b#c"})
    void rejectsMissingOrInvalidRiotIdBeforeNetwork(String id) {
        // given
        RiotClient client = new RiotClient(WebClient.builder().exchangeFunction(request -> {
            fail("유효하지 않은 Riot ID로 외부 요청이 발생함");
            return Mono.empty();
        }), "test-key");
        // when / then
        assertError(ErrorCode.INVALID_RIOT_ID, () -> client.findSoloRank(id));
    }

    @Test
    void missingKeyFailsBeforeNetwork() {
        // given
        RiotClient client = new RiotClient(WebClient.builder(), "");
        // when / then
        assertError(ErrorCode.RIOT_NOT_CONFIGURED, () -> client.findSoloRank("player#KR1"));
    }

    private RiotClient clientReturningLeague(String body) {
        return new RiotClient(WebClient.builder().exchangeFunction(request ->
                request.url().getHost().startsWith("asia") ? json("{\"puuid\":\"puuid\"}") : json(body)), "test-key");
    }

    private Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build());
    }

    private void assertError(ErrorCode expected, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, exception.getErrorCode());
    }
}
