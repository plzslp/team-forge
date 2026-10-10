package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:game-profile-list-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
@Transactional
class GameProfileListApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;

    @Test
    void listIncludesOnlyActiveParticipantsWithSelectedGameAndCompletePositions() throws Exception {
        // given: 게임·삭제·프로필 유무가 다른 참여자들을 저장한다.
        Participant zed = saveProfile("Zed", Game.LOL, 1200, 1500, Set.of(Position.LOL_MID));
        Participant amy = saveProfile("Amy", Game.LOL, 1200, 0, Set.of(Position.LOL_TOP, Position.LOL_SUPPORT));
        em.persist(new GameProfile(amy.getId(), Game.OVERWATCH, "ow-account", 900, null,
                Set.of(Position.OVERWATCH_TANK)));
        Participant deleted = saveProfile("Aaron", Game.LOL, 1000, null, Set.of(Position.LOL_TOP));
        deleted.delete(Instant.EPOCH);
        saveProfile("OtherGame", Game.OVERWATCH, 1000, null, Set.of(Position.OVERWATCH_SUPPORT));
        em.persist(Participant.register("NoProfile"));
        flushAndClear();

        // when / then: 요청한 게임의 활성 프로필만 이름순으로 반환한다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].participantId").value(amy.getId().toString()))
                .andExpect(jsonPath("$.content[0].profileId").isString())
                .andExpect(jsonPath("$.content[0].name").value("Amy"))
                .andExpect(jsonPath("$.content[0].game").value("LOL"))
                .andExpect(jsonPath("$.content[0].accountId").value("account-Amy"))
                .andExpect(jsonPath("$.content[0].effectiveScore").value(0))
                .andExpect(jsonPath("$.content[0].preferredPositions", containsInAnyOrder("LOL_TOP", "LOL_SUPPORT")))
                .andExpect(jsonPath("$.content[1].participantId").value(zed.getId().toString()))
                .andExpect(jsonPath("$.content[1].effectiveScore").value(1500))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get("/api/game-profiles").param("game", "OVERWATCH").param("keyword", "Amy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].effectiveScore").value(900));
    }

    @Test
    void filtersAreCombinedAndDoNotRemoveOtherPreferredPositions() throws Exception {
        // given: 이름과 포지션이 일부씩 일치하는 세 참여자를 준비한다.
        saveProfile("Alpha", Game.LOL, 1000, null, Set.of(Position.LOL_TOP, Position.LOL_SUPPORT));
        saveProfile("Alpine", Game.LOL, 1000, null, Set.of(Position.LOL_MID));
        saveProfile("Beta", Game.LOL, 1000, null, Set.of(Position.LOL_TOP));
        flushAndClear();
        // when / then: 대소문자·앞뒤 공백을 무시한 이름 검색과 포지션 조건을 모두 적용한다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL")
                        .param("keyword", "  ALP  ").param("position", "LOL_TOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.content[0].preferredPositions", containsInAnyOrder("LOL_TOP", "LOL_SUPPORT")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("position", "LOL_TOP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("keyword", "ALP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void searchTreatsLikeWildcardsAndEscapeCharacterAsLiteralText(String keyword) throws Exception {
        // given: LIKE 특수 문자를 포함한 이름과 일반 이름을 저장한다.
        saveProfile("Name" + keyword, Game.LOL, 1000, null, Set.of(Position.LOL_TOP));
        saveProfile("Other", Game.LOL, 1000, null, Set.of(Position.LOL_TOP));
        flushAndClear();
        // when / then: 특수 문자를 와일드카드 대신 실제 이름의 문자로 검색한다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("keyword", keyword))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Name" + keyword));
    }

    @Test
    void paginationHasStableIdOrderAccurateFilteredTotalsAndEmptyLastPage() throws Exception {
        // given: 복수 포지션이 있는 동명이인과 필터에서 제외되는 참여자를 준비한다.
        IntStream.range(0, 3).forEach(i -> saveProfile("Same", Game.LOL, 1000, null,
                Set.of(Position.LOL_TOP, Position.LOL_SUPPORT)));
        saveProfile("Other", Game.LOL, 1000, null, Set.of(Position.LOL_MID));
        flushAndClear();
        List<UUID> ids = em.createQuery("select p.id from Participant p where p.name = 'Same' order by p.id", UUID.class)
                .getResultList();
        // when / then: 동일 이름은 ID순이며 sort 입력은 고정 정렬을 변경하지 않는다.
        for (int page = 0; page < 3; page++) {
            mvc.perform(get("/api/game-profiles").param("game", "LOL").param("position", "LOL_TOP")
                            .param("page", String.valueOf(page)).param("size", "1").param("sort", "name,desc"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].participantId").value(ids.get(page).toString()))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("position", "LOL_TOP")
                        .param("page", "3").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void defaultAndMaximumPageSizeAndBlankSearchWork() throws Exception {
        // given: 기본 페이지보다 많은 프로필을 저장한다.
        IntStream.range(0, 21).forEach(i -> saveProfile("Player" + i, Game.LOL, 1000, null, Set.of(Position.LOL_TOP)));
        flushAndClear();
        // when / then: 기본 20개, 최대 100개와 생략한 size의 기본값을 확인한다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("keyword", "  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.totalElements").value(21)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(21)));
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void emptyAndUnmatchedListsReturnZeroTotals() throws Exception {
        // given: 프로필이 없는 DB를 준비한다.
        // when / then: 빈 목록은 404가 아닌 페이지 정보가 포함된 200이다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        saveProfile("Player", Game.LOL, 1000, null, Set.of(Position.LOL_TOP));
        flushAndClear();
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("keyword", "Missing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?game=", "?game=OW", "?game=LOL&position=UNKNOWN",
            "?game=LOL&position=OVERWATCH_TANK", "?game=LOL&keyword=123456789012345678901"})
    void invalidGameSearchAndPositionReturn400(String query) throws Exception {
        // given: 필수값 누락, 잘못된 형식 또는 게임과 맞지 않는 검색 조건을 준비한다.
        // when / then: 공통 입력 오류를 반환한다.
        mvc.perform(get("/api/game-profiles" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "abc,20", "0,abc",
            "2147483648,20", "0,2147483648", "2147483647,100", "'',20", "0,''", "'',''", "' ',20", "0,' '"})
    void invalidPaginationReturns400(String page, String size) throws Exception {
        // given: 잘못된 형식·범위 또는 명시적인 빈 페이징 값을 준비한다.
        // when / then: 기본값으로 대체하지 않고 공통 400을 반환한다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("page", page).param("size", size))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listReadsPositionsInBulkInsteadOfPerProfileQueries() throws Exception {
        // given: 페이지보다 많은 프로필을 저장하고 DB 조회 통계를 초기화한다.
        IntStream.range(0, 10).forEach(i -> saveProfile("Player" + i, Game.LOL, 1000, null,
                Set.of(Position.LOL_TOP, Position.LOL_SUPPORT)));
        flushAndClear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        // when: 프로필 5개와 복수 포지션을 조회한다.
        mvc.perform(get("/api/game-profiles").param("game", "LOL").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[4].preferredPositions", hasSize(2)));
        // then: 페이지 대상·전체 개수·프로필과 포지션 묶음 조회의 SQL 3개만 실행한다.
        assertEquals(3, statistics.getPrepareStatementCount());
    }

    @Test
    void openApiDocumentsQueryParametersAndResponse() throws Exception {
        // given: 실제 Controller와 문서용 Api가 등록되어 있다.
        // when / then: 필수 게임과 페이지 기본값, 응답·오류 계약을 문서화한다.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/game-profiles'].get.summary").value("게임별 참여자 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/game-profiles'].get.parameters[?(@.name == 'game')].required")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.paths['/api/game-profiles'].get.parameters[?(@.name == 'page')].schema.default")
                        .value(hasItem(0)))
                .andExpect(jsonPath("$.paths['/api/game-profiles'].get.parameters[?(@.name == 'size')].schema.default")
                        .value(hasItem(20)))
                .andExpect(jsonPath("$.paths['/api/game-profiles'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/GameProfilePageResponse"))
                .andExpect(jsonPath("$.paths['/api/game-profiles'].get.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"));
    }

    private Participant saveProfile(String name, Game game, int baseScore, Integer manualScore, Set<Position> positions) {
        Participant participant = Participant.register(name);
        em.persist(participant);
        em.persist(new GameProfile(participant.getId(), game, "account-" + name, baseScore, manualScore, positions));
        return participant;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
