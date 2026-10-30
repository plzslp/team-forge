package com.example.teamforge.match.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.dto.MatchCreateRequest;
import com.example.teamforge.match.dto.MatchCreateResponse;
import com.example.teamforge.match.dto.MatchParticipantRequest;
import com.example.teamforge.match.dto.TeamPreviewRequest;
import com.example.teamforge.match.dto.TeamPreviewResponse;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.entity.MatchResult;
import com.example.teamforge.match.service.TeamPreviewService;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-query-api-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@Transactional
class MatchQueryApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired TeamPreviewService previewService;
    @Autowired JsonMapper json;
    private final Map<Game, List<UUID>> participantIds = new EnumMap<>(Game.class);

    @ParameterizedTest
    @EnumSource(Game.class)
    void readsStoredTeamsAndDistinguishesUnrecordedResult(Game game) throws Exception {
        // given: 스냅샷의 합계는 int 범위를 넘으며 경기 결과는 아직 없다.
        UUID id = persistMatch(1, game, Instant.EPOCH, null);
        flushAndClear();

        // when
        ResultActions result = mvc.perform(get("/api/matches/" + id));

        // then: 생성 시각·역할·총점은 저장된 값이고 내부 엔티티 필드는 노출하지 않는다.
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.game").value(game.name()))
                .andExpect(jsonPath("$.createdAt").value(Instant.EPOCH.toString()))
                .andExpect(jsonPath("$.teams[0].team").value("A"))
                .andExpect(jsonPath("$.teams[1].team").value("B"))
                .andExpect(jsonPath("$.teams[0].participants", hasSize(5)))
                .andExpect(jsonPath("$.teams[1].participants", hasSize(5)))
                .andExpect(jsonPath("$.teams[0].totalScore").value(4L * Integer.MAX_VALUE))
                .andExpect(jsonPath("$.teams[1].totalScore").value(5L * Integer.MAX_VALUE))
                .andExpect(jsonPath("$.scoreDifference").value((long) Integer.MAX_VALUE))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.teams[*].participants[*].id").doesNotExist())
                .andExpect(jsonPath("$.teams[*].participants[*].accountId").doesNotExist());
        if (game == Game.LOL) {
            result.andExpect(jsonPath("$.teams[0].participants[*].assignedPosition", contains(
                    "LOL_TOP", "LOL_JUNGLE", "LOL_MID", "LOL_BOTTOM", "LOL_SUPPORT")));
        } else {
            result.andExpect(jsonPath("$.teams[0].participants[*].assignedPosition", contains(
                    "OVERWATCH_TANK", "OVERWATCH_DAMAGE", "OVERWATCH_DAMAGE", "OVERWATCH_SUPPORT", "OVERWATCH_SUPPORT")));
        }
    }

    @ParameterizedTest
    @CsvSource({"3,1,WIN,LOSS", "1,3,LOSS,WIN", "0,0,DRAW,DRAW"})
    void readsRecordedScoreMemoAndOutcomesInListAndDetail(int scoreA, int scoreB, String outcomeA, String outcomeB) throws Exception {
        // given: 저장된 결과가 있는 경기를 준비한다. 0:0도 미입력이 아닌 입력된 무승부다.
        UUID id = persistMatch(1, Game.LOL, Instant.EPOCH, new MatchResult(scoreA, scoreB, "경기 메모"));
        flushAndClear();

        // when
        ResultActions detail = mvc.perform(get("/api/matches/" + id));
        ResultActions list = mvc.perform(get("/api/matches?game=LOL"));

        // then: 두 조회의 경기 결과가 같다.
        detail.andExpect(status().isOk()).andExpect(jsonPath("$.result.scoreA").value(scoreA))
                .andExpect(jsonPath("$.result.scoreB").value(scoreB))
                .andExpect(jsonPath("$.result.memo").value("경기 메모"))
                .andExpect(jsonPath("$.result.outcomeA").value(outcomeA))
                .andExpect(jsonPath("$.result.outcomeB").value(outcomeB));
        list.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].result.scoreA").value(scoreA))
                .andExpect(jsonPath("$.content[0].result.scoreB").value(scoreB))
                .andExpect(jsonPath("$.content[0].result.outcomeA").value(outcomeA))
                .andExpect(jsonPath("$.content[0].result.outcomeB").value(outcomeB));
    }

    @Test
    void listFiltersGameAndOrdersByCreatedAtThenIdDescending() throws Exception {
        // given: 날짜가 다른 경기와 같은 시각·서로 다른 ID인 경기를 준비한다.
        UUID oldest = persistMatch(1, Game.LOL, Instant.EPOCH, null);
        UUID tiedLower = persistMatch(2, Game.LOL, Instant.EPOCH.plusSeconds(1), null);
        UUID tiedHigher = persistMatch(3, Game.LOL, Instant.EPOCH.plusSeconds(1), null);
        UUID overwatch = persistMatch(4, Game.OVERWATCH, Instant.EPOCH.plusSeconds(2), null);
        flushAndClear();

        // when: 지원하지 않는 sort를 전달해도 서버가 정한 순서를 유지한다.
        ResultActions result = mvc.perform(get("/api/matches?game=LOL&size=2&sort=createdAt,asc"));

        // then: 다른 게임은 제외하고 같은 날짜에서는 ID 내림차순이다.
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(tiedHigher.toString(), tiedLower.toString())))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));

        // when: 다음 페이지와 다른 게임을 각각 조회한다.
        ResultActions next = mvc.perform(get("/api/matches?game=LOL&size=2&page=1"));
        ResultActions otherGame = mvc.perform(get("/api/matches?game=OVERWATCH"));

        // then: 마지막 페이지와 게임별 총 개수도 필터 기준이다.
        next.andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(oldest.toString())))
                .andExpect(jsonPath("$.totalElements").value(3));
        otherGame.andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(overwatch.toString())))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void returnsDefaultPageAndAllowsSizeOneHundred() throws Exception {
        // given: 기본 페이지 크기보다 많은 경기를 준비한다.
        for (int index = 1; index <= 21; index++) persistMatch(index, Game.LOL, Instant.EPOCH.plusSeconds(index), null);
        flushAndClear();

        // when
        ResultActions defaults = mvc.perform(get("/api/matches?game=LOL"));
        ResultActions maximum = mvc.perform(get("/api/matches?game=LOL&size=100"));

        // then: 기본은 20개, 허용 최대 크기는 100개다.
        defaults.andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21)).andExpect(jsonPath("$.totalPages").value(2));
        maximum.andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(21)))
                .andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void emptyAndBeyondLastPagesKeepCorrectTotals() throws Exception {
        // given: 롤 기록만 한 건 있다.
        persistMatch(1, Game.LOL, Instant.EPOCH, null);
        flushAndClear();

        // when
        ResultActions empty = mvc.perform(get("/api/matches?game=OVERWATCH"));
        ResultActions beyond = mvc.perform(get("/api/matches?game=LOL&page=100&size=1"));

        // then: 미존재 게임 결과는 0, 마지막 페이지 이후는 실제 총 개수를 유지한다.
        empty.andExpect(status().isOk()).andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        beyond.andExpect(status().isOk()).andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.page").value(100)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void originalParticipantAndProfileChangesDoNotAffectHistory() throws Exception {
        // given: 경기 확정 당시의 이름·점수·포지션이 저장돼 있다.
        UUID id = persistMatch(1, Game.LOL, Instant.EPOCH, null);
        UUID player = idsFor(Game.LOL).get(0);
        Participant participant = em.find(Participant.class, player);

        // when: 원본 이름·점수·포지션을 바꾸고 참가자를 삭제한다.
        participant.rename("현재의 이름");
        participant.delete(Instant.now());
        GameProfile profile = em.createQuery("select g from GameProfile g where g.participantId = :id", GameProfile.class)
                .setParameter("id", player).getSingleResult();
        profile.overrideScore(999).changePreferredPositions(Set.of(Position.LOL_SUPPORT));
        flushAndClear();
        ResultActions detail = mvc.perform(get("/api/matches/" + id));
        ResultActions list = mvc.perform(get("/api/matches?game=LOL"));

        // then: 삭제된 참가자도 과거 이름·0점·탑 배정으로 남아 있다.
        detail.andExpect(status().isOk()).andExpect(jsonPath("$.teams[0].participants[0].participantId").value(player.toString()))
                .andExpect(jsonPath("$.teams[0].participants[0].name").value("참가자0"))
                .andExpect(jsonPath("$.teams[0].participants[0].score").value(0))
                .andExpect(jsonPath("$.teams[0].participants[0].assignedPosition").value("LOL_TOP"));
        list.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].teams[0].participants[0].name").value("참가자0"));
    }

    @Test
    void pageLoadsAllSnapshotsWithoutPerMatchQueriesOrCollectionPagination() throws Exception {
        // given: 한 페이지에는 5경기·50명의 스냅샷을 읽고, 총 개수를 위한 다음 페이지도 있다.
        for (int index = 1; index <= 10; index++) persistMatch(index, Game.LOL, Instant.EPOCH.plusSeconds(index), null);
        flushAndClear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        ResultActions result = mvc.perform(get("/api/matches?game=LOL&size=5"));

        // then: ID 페이지·count·스냅샷 일괄 조회 3개만 실행한다.
        result.andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[*].teams[*].participants[*].participantId", hasSize(50)))
                .andExpect(jsonPath("$.totalElements").value(10));
        assertEquals(3L, statistics.getPrepareStatementCount());
    }

    @Test
    void detailLoadsMatchAndSnapshotsInOneQuery() throws Exception {
        // given
        UUID id = persistMatch(1, Game.LOL, Instant.EPOCH, null);
        flushAndClear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        ResultActions result = mvc.perform(get("/api/matches/" + id));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.teams[0].participants", hasSize(5)));
        assertEquals(1L, statistics.getPrepareStatementCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?game=", "?game=UNKNOWN", "?game=LOL&page=", "?game=LOL&size=",
            "?game=LOL&page=%20", "?game=LOL&size=%20", "?game=LOL&page=-1", "?game=LOL&size=0",
            "?game=LOL&size=101", "?game=LOL&page=abc", "?game=LOL&size=abc", "?game=LOL&page=2147483648",
            "?game=LOL&page=2147483647&size=100", "?game=LOL&size=2147483648"})
    void invalidGameAndPaginationReturnCommonError(String query) throws Exception {
        // given: 필수 게임이나 페이지 형식·범위가 잘못된 요청이다.
        // when / then
        mvc.perform(get("/api/matches" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingMatchAndMalformedUuidHaveDifferentErrors() throws Exception {
        // given: 존재하지 않는 경기 ID와 UUID 형식 오류이다.
        // when / then
        mvc.perform(get("/api/matches/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/matches/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void creationLocationLeadsToStoredDetail() throws Exception {
        // given: 실제 미리보기로 확정 요청을 준비한다.
        List<UUID> ids = idsFor(Game.LOL);
        flushAndClear();
        TeamPreviewResponse preview = previewService.create(new TeamPreviewRequest(Game.LOL, ids, false));
        List<MatchParticipantRequest> members = preview.teams().stream().flatMap(team -> team.participants().stream()
                .map(member -> new MatchParticipantRequest(member.participantId(), team.team(), member.assignedPosition()))).toList();
        MatchCreateRequest request = new MatchCreateRequest(Game.LOL, members, preview.participantVersions(), false);

        // when: 새 경기를 생성한다.
        MockHttpServletResponse created = mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request))).andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/matches/"))).andReturn().getResponse();
        String location = created.getHeader("Location");
        UUID id = UUID.fromString(location.substring("/api/matches/".length()));
        em.clear();

        // then: Location과 본문의 ID가 일치하고 상세 조회가 가능하다.
        assertEquals(id, json.readValue(created.getContentAsString(), MatchCreateResponse.class).id());
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    void documentsReadContractOnApiClass() throws Exception {
        // given: 실제 OpenAPI 문서의 상속된 Api 계약이다.
        // when
        ResultActions result = mvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/matches'].get.summary").value("경기 기록 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/matches/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/matches'].get.parameters[?(@.name == 'game')].required", contains(true)))
                .andExpect(jsonPath("$.paths['/api/matches'].get.parameters[?(@.name == 'page')].schema.default", contains(0)))
                .andExpect(jsonPath("$.paths['/api/matches'].get.parameters[?(@.name == 'size')].schema.default", contains(20)))
                .andExpect(jsonPath("$.components.schemas.MatchResponse.properties.result").exists());
    }

    private UUID persistMatch(long number, Game game, Instant createdAt, MatchResult result) {
        List<UUID> ids = idsFor(game);
        List<MatchParticipant> snapshots = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            snapshots.add(new MatchParticipant(ids.get(index), "참가자" + index, index == 0 ? 0 : Integer.MAX_VALUE,
                    game.teamPositions().get(index % 5), index < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B));
        }
        UUID id = new UUID(0, number);
        em.persist(new Match(id, game, createdAt, snapshots, result));
        return id;
    }

    private List<UUID> idsFor(Game game) {
        return participantIds.computeIfAbsent(game, selectedGame -> {
            List<UUID> ids = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                Participant participant = Participant.register("참가자" + index);
                em.persist(participant);
                em.persist(new GameProfile(participant.getId(), selectedGame, null, Integer.MAX_VALUE,
                        index == 0 ? 0 : null, Set.copyOf(selectedGame.teamPositions())));
                ids.add(participant.getId());
            }
            return ids;
        });
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
