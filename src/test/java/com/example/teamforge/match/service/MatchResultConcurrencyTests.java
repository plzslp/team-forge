package com.example.teamforge.match.service;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.entity.MatchResult;
import com.example.teamforge.match.repository.MatchRepository;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:match-result-concurrency-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class MatchResultConcurrencyTests {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean MatchRepository matchRepository;
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
    void overlappingResultUpdatesReturnConflictAndPreserveCommittedWinner(boolean hasResult) throws Exception {
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

        // given: 요청이 실제 DB에서 이전 버전을 읽은 직후 다른 트랜잭션이 결과를 커밋한다.
        doAnswer(invocation -> {
            // Spring Data 프록시의 실제 쿼리는 spy의 기본 위임으로 실행한다.
            Object loaded = mockingDetails(invocation.getMock()).getMockCreationSettings()
                    .getDefaultAnswer().answer(invocation);
            TransactionTemplate independent = transaction();
            independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(status ->
                    em.find(Match.class, id).recordResult(new MatchResult(2, 1, "먼저 커밋")));
            return loaded;
        }).when(matchRepository).findWithParticipantsById(id);

        // when: 외부 테스트 트랜잭션 없이 요청을 실행한다.
        mvc.perform(put("/api/matches/" + id + "/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scoreA\":1,\"scoreB\":3,\"memo\":\"충돌 요청\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // then: 요청 종료 후 새 트랜잭션에서 먼저 커밋한 결과를 확인한다.
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
