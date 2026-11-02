package com.example.teamforge.match.service;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.match.dto.request.MatchResultRequest;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.entity.MatchResult;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:match-result-concurrency-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class MatchResultConcurrencyTests {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MatchService matchService;
    @Autowired MockMvc mvc;

    @AfterEach
    void cleanDedicatedDatabase() {
        transaction().executeWithoutResult(status -> {
            em.createQuery("delete from MatchParticipant").executeUpdate();
            em.createQuery("delete from Match").executeUpdate();
            em.createQuery("delete from Participant").executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overlappingResultUpdatesReturnConflictAndPreserveCommittedWinner(boolean hasResult) {
        // given: 결과 최초 입력과 기존 결과 수정 양쪽에서 독립 트랜잭션의 충돌을 검증한다.
        UUID id = transaction().execute(status -> {
            List<MatchParticipant> members = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                Participant participant = Participant.register("참가자" + index);
                em.persist(participant);
                members.add(new MatchParticipant(participant.getId(), participant.getName(), 1000,
                        Game.LOL.teamPositions().get(index % 5), index < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B));
            }
            UUID matchId = UUID.randomUUID();
            em.persist(new Match(matchId, Game.LOL, Instant.EPOCH, members,
                    hasResult ? new MatchResult(0, 0, "기존 결과") : null));
            return matchId;
        });

        // when: 먼저 연 트랜잭션이 읽은 버전을 유지한 채, 다른 연결에서 결과 수정을 커밋한다.
        transaction().executeWithoutResult(status -> {
            Match stale = em.find(Match.class, id);
            long previousVersion = stale.getVersion();
            TransactionTemplate independent = transaction();
            independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(inner -> matchService.recordResult(id, new MatchResultRequest(2, 1, "먼저 커밋")));
            assertEquals(previousVersion, stale.getVersion());

            // then: 이전 버전으로 실행한 실제 HTTP 수정은 공통 409 응답이며 롤백된다.
            try {
                mvc.perform(put("/api/matches/" + id + "/result").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scoreA\":1,\"scoreB\":3,\"memo\":\"충돌 요청\"}"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                status.setRollbackOnly();
            }
        });
        transaction().executeWithoutResult(status -> {
            Match saved = em.find(Match.class, id);
            assertEquals(2, saved.getResult().getScoreA());
            assertEquals(1, saved.getResult().getScoreB());
            assertEquals("먼저 커밋", saved.getResult().getMemo());
            assertEquals(10, saved.getParticipants().size());
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
