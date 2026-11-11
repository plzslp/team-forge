package com.example.teamforge.match.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.entity.MatchParticipant.Team;
import com.example.teamforge.match.entity.MatchResult;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.teamforge.support.AuthenticatedMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:participant-history-test;DB_CLOSE_DELAY=-1")
@AuthenticatedMockMvc
@Transactional
class ParticipantMatchHistoryApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @Test
    void filtersAndPagesHistoryWithOutcomesFromParticipantPerspective() throws Exception {
        // given: 두 게임에 참가했고, 같은 시각의 기록과 아직 결과가 없는 기록도 있다.
        List<Participant> participants = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Participant participant = Participant.register("참가자" + i);
            em.persist(participant);
            participants.add(participant);
        }
        UUID loss = persistMatch(1, Instant.EPOCH, Game.LOL, participants.subList(0, 10), Team.B, new MatchResult(2, 1, null));
        UUID draw = persistMatch(2, Instant.EPOCH, Game.LOL, participants.subList(0, 10), Team.A, new MatchResult(0, 0, null));
        UUID unrecorded = persistMatch(3, Instant.EPOCH.plusSeconds(1), Game.LOL, participants.subList(0, 10), Team.A, null);
        UUID win = persistMatch(4, Instant.EPOCH.plusSeconds(2), Game.LOL, participants.subList(0, 10), Team.B, new MatchResult(1, 2, null));
        persistMatch(5, Instant.EPOCH.plusSeconds(3), Game.OVERWATCH, participants.subList(0, 10), Team.A, null);
        persistMatch(6, Instant.EPOCH.plusSeconds(4), Game.LOL, participants.subList(1, 11), Team.A, null);
        em.flush();
        em.clear();
        String url = "/api/participants/" + participants.get(0).getId() + "/matches";

        // when / then: 참가하지 않은 경기와 다른 게임은 제외하며, 페이지 경계에서도 최신순을 유지한다.
        mvc.perform(get(url + "?game=LOL&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].matchId", contains(win.toString(), unrecorded.toString())))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].team").value("B"))
                .andExpect(jsonPath("$.content[0].scoreA").value(1))
                .andExpect(jsonPath("$.content[0].scoreB").value(2))
                .andExpect(jsonPath("$.content[0].outcome").value("WIN"))
                .andExpect(jsonPath("$.content[1].scoreA").value(nullValue()))
                .andExpect(jsonPath("$.content[1].scoreB").value(nullValue()))
                .andExpect(jsonPath("$.content[1].outcome").value(nullValue()));
        mvc.perform(get(url + "?game=LOL&page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].matchId", contains(draw.toString(), loss.toString())))
                .andExpect(jsonPath("$.content[0].scoreA").value(0))
                .andExpect(jsonPath("$.content[0].scoreB").value(0))
                .andExpect(jsonPath("$.content[0].outcome").value("DRAW"))
                .andExpect(jsonPath("$.content[1].outcome").value("LOSS"));
        mvc.perform(get(url + "?game=LOL&page=2&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void returnsEmptyHistoryWithDefaultsAndRejectsDeletedOrUnknownParticipants() throws Exception {
        // given
        Participant active = Participant.register("활성");
        Participant deleted = Participant.register("삭제됨");
        deleted.delete(Instant.EPOCH);
        em.persist(active);
        em.persist(deleted);
        em.flush();

        // when / then
        mvc.perform(get("/api/participants/" + active.getId() + "/matches?game=OVERWATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0));
        for (UUID id : List.of(deleted.getId(), UUID.randomUUID())) {
            mvc.perform(get("/api/participants/" + id + "/matches?game=LOL"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?game=", "?game=OTHER", "?game=LOL&page=", "?game=LOL&page=-1",
            "?game=LOL&size=", "?game=LOL&size=0", "?game=LOL&size=101", "?game=LOL&page=2147483647&size=100"})
    void rejectsInvalidGameOrPagination(String query) throws Exception {
        // when / then
        mvc.perform(get("/api/participants/" + UUID.randomUUID() + "/matches" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private UUID persistMatch(long id, Instant createdAt, Game game, List<Participant> participants,
                              Team firstTeam, MatchResult result) {
        List<MatchParticipant> snapshots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Participant participant = participants.get(i);
            Team otherTeam = firstTeam == Team.A ? Team.B : Team.A;
            snapshots.add(new MatchParticipant(participant.getId(), participant.getName(), 1000,
                    game.teamPositions().get(i % 5), i < 5 ? firstTeam : otherTeam));
        }
        UUID matchId = new UUID(0, id);
        em.persist(new Match(matchId, game, createdAt, snapshots, result));
        return matchId;
    }
}
