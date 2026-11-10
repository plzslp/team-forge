package com.example.teamforge.external.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class RiotClient {
    private final WebClient accountClient;
    private final WebClient leagueClient;
    private final String apiKey;
    private final AtomicReference<Instant> retryAt = new AtomicReference<>(Instant.EPOCH);

    public RiotClient(WebClient.Builder builder, @Value("${riot.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.accountClient = builder.clone().baseUrl("https://asia.api.riotgames.com")
                .defaultHeader("X-Riot-Token", apiKey).build();
        this.leagueClient = builder.clone().baseUrl("https://kr.api.riotgames.com")
                .defaultHeader("X-Riot-Token", apiKey).build();
    }

    public RiotRank findSoloRank(String riotId) {
        if (riotId == null) throw new BusinessException(ErrorCode.INVALID_RIOT_ID);
        String[] parts = riotId.split("#", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_RIOT_ID);
        }
        Account account = get(accountClient, "/riot/account/v1/accounts/by-riot-id/{name}/{tag}",
                new ParameterizedTypeReference<Account>() {}, ErrorCode.RIOT_ACCOUNT_NOT_FOUND, parts[0], parts[1]);
        if (account.puuid() == null || account.puuid().isBlank() || account.puuid().length() > 255) {
            throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        }
        List<LeagueEntry> entries = get(leagueClient, "/lol/league/v4/entries/by-puuid/{puuid}",
                new ParameterizedTypeReference<List<LeagueEntry>>() {}, ErrorCode.RIOT_UNAVAILABLE, account.puuid());
        if (entries.stream().anyMatch(entry -> entry == null || entry.queueType() == null)) {
            throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        }
        List<LeagueEntry> soloEntries = entries.stream()
                .filter(entry -> entry != null && "RANKED_SOLO_5x5".equals(entry.queueType())).toList();
        if (soloEntries.size() > 1) throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        if (soloEntries.isEmpty()) return new RiotRank(account.puuid(), null, null, null);
        LeagueEntry entry = soloEntries.get(0);
        if (entry.tier() == null || entry.rank() == null || entry.rank().length() > 5) {
            throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        }
        RiotRank rank = new RiotRank(account.puuid(), entry.tier(), entry.rank(), entry.leaguePoints());
        rank.score();
        return rank;
    }

    @SuppressWarnings("PMD.PreserveStackTrace") // 원인 예외의 URI·인증 헤더·응답 본문 노출을 방지한다.
    private <T> T get(WebClient client, String path, ParameterizedTypeReference<T> type,
                      ErrorCode notFound, Object... variables) {
        if (apiKey.isBlank()) throw new BusinessException(ErrorCode.RIOT_NOT_CONFIGURED);
        if (Instant.now().isBefore(retryAt.get())) throw new BusinessException(ErrorCode.RIOT_RATE_LIMITED);
        String host = client == accountClient ? "asia.api.riotgames.com" : "kr.api.riotgames.com";
        AtomicInteger receivedStatus = new AtomicInteger();
        try {
            return client.get().uri(path, variables).exchangeToMono(response -> {
                int status = response.statusCode().value();
                receivedStatus.set(status);
                if (status == 200) return response.bodyToMono(type);
                ErrorCode error = switch (status) {
                    case 401, 403 -> ErrorCode.RIOT_AUTHENTICATION_FAILED;
                    case 404 -> notFound;
                    case 429 -> {
                        Instant until;
                        try {
                            long seconds = Long.parseLong(response.headers().asHttpHeaders().getFirst("Retry-After"));
                            until = Instant.now().plusSeconds(Math.max(1, seconds));
                        } catch (RuntimeException exception) {
                            until = Instant.now().plusSeconds(60);
                        }
                        Instant blockedUntil = until;
                        retryAt.updateAndGet(previous -> previous.isAfter(blockedUntil) ? previous : blockedUntil);
                        yield ErrorCode.RIOT_RATE_LIMITED;
                    }
                    default -> ErrorCode.RIOT_UNAVAILABLE;
                };
                return response.releaseBody().then(Mono.<T>error(new BusinessException(error)));
            }).switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RIOT_UNAVAILABLE)))
                    .timeout(Duration.ofSeconds(5)).block();
        } catch (RuntimeException exception) {
            log.warn("Riot 호출 실패: host={}, status={}, cause={}", host,
                    receivedStatus.get() == 0 ? "unknown" : receivedStatus.get(),
                    exception.getClass().getSimpleName());
            if (exception instanceof BusinessException businessException) throw businessException;
            // 외부 응답·URI·인증 헤더가 공통 예외 로그나 응답에 노출되지 않도록 변환한다.
            throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String puuid) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueEntry(String queueType, String tier, String rank, Integer leaguePoints) {}
}
