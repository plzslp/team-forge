package com.example.teamforge.match.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.dto.MatchCreateRequest;
import com.example.teamforge.match.dto.MatchCreateResponse;
import com.example.teamforge.match.dto.MatchParticipantRequest;
import com.example.teamforge.match.dto.TeamPreviewRequest;
import com.example.teamforge.match.dto.TeamPreviewResponse;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/** 롤백되는 단일 테스트 트랜잭션 대신, 실제로 커밋되는 독립 트랜잭션 두 개를 사용한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:match-confirmation-lock-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000")
class MatchConfirmationConcurrencyTests {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MatchService matchService;
    @Autowired TeamPreviewService previewService;
    private ExecutorService workers;
    private MatchCreateRequest request;
    private UUID participantId;

    @BeforeEach
    void prepareCommittedPreview() {
        workers = Executors.newFixedThreadPool(2);
        List<UUID> ids = transaction().execute(status -> {
            List<UUID> result = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                Participant participant = Participant.register("참가자" + index);
                em.persist(participant);
                em.persist(new GameProfile(participant.getId(), Game.LOL, null, 1200, null, Set.copyOf(Game.LOL.teamPositions())));
                result.add(participant.getId());
            }
            return result;
        });
        TeamPreviewResponse preview = previewService.create(new TeamPreviewRequest(Game.LOL, ids, false));
        List<MatchParticipantRequest> members = preview.teams().stream().flatMap(team -> team.participants().stream()
                .map(member -> new MatchParticipantRequest(member.participantId(), team.team(), member.assignedPosition()))).toList();
        request = new MatchCreateRequest(Game.LOL, members, preview.participantVersions(), false);
        participantId = members.get(0).participantId();
    }

    @AfterEach
    void stopWorkersAndCleanDedicatedTestDatabase() throws InterruptedException {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        // 이 클래스 전용 H2 DB의 커밋된 테스트 데이터를 FK의 자식부터 지운다.
        transaction().executeWithoutResult(status -> {
            em.createQuery("delete from MatchParticipant").executeUpdate();
            em.createQuery("delete from Match").executeUpdate();
            em.createNativeQuery("delete from game_profile_positions").executeUpdate();
            em.createQuery("delete from GameProfile").executeUpdate();
            em.createQuery("delete from Participant").executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "deleted", "score", "positions"})
    void confirmationKeepsParticipantAndProfileLockedUntilCommit(String change) throws Exception {
        // given: 확정 서비스 반환 후에도 바깥 트랜잭션을 열어 잠금 유지 구간을 관찰한다.
        CountDownLatch confirmed = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch updateAttempted = new CountDownLatch(1);
        Future<MatchCreateResponse> confirmation = workers.submit(() -> transaction().execute(status -> {
            MatchCreateResponse result = matchService.create(request);
            confirmed.countDown();
            await(allowCommit);
            return result;
        }));
        try {
            assertTrue(confirmed.await(5, TimeUnit.SECONDS));
            Future<?> update = workers.submit(() -> transaction().executeWithoutResult(status -> {
                changeCurrentInformation(change, false);
                updateAttempted.countDown();
                em.flush();
            }));

            // when: 다른 트랜잭션이 참가자 또는 프로필 변경을 DB에 반영하려 한다.
            assertTrue(updateAttempted.await(5, TimeUnit.SECONDS));

            // then: 확정 트랜잭션이 끝나기 전에는 변경을 완료할 수 없다.
            assertThrows(TimeoutException.class, () -> update.get(200, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            MatchCreateResponse saved = confirmation.get(5, TimeUnit.SECONDS);
            update.get(5, TimeUnit.SECONDS);
            transaction().executeWithoutResult(status -> {
                String name = em.createQuery("select p.name from MatchParticipant p where p.participantId = :id", String.class)
                        .setParameter("id", participantId).getSingleResult();
                int score = em.createQuery("select p.score from MatchParticipant p where p.participantId = :id", Integer.class)
                        .setParameter("id", participantId).getSingleResult();
                assertEquals(1L, em.createQuery("select count(m) from Match m where m.id = :id", Long.class)
                        .setParameter("id", saved.id()).getSingleResult());
                assertNotEquals("수정된 이름", name);
                assertEquals(1200, score);
            });
        } finally {
            allowCommit.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "deleted", "score", "positions"})
    void modificationThatLocksFirstMakesWaitingPreviewOutdated(String change) throws Exception {
        // given: 수정 트랜잭션이 먼저 행을 잠그고, 변경은 아직 커밋하지 않는다.
        CountDownLatch modified = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch confirmationAttempted = new CountDownLatch(1);
        Future<?> update = workers.submit(() -> transaction().executeWithoutResult(status -> {
            changeCurrentInformation(change, true);
            em.flush();
            modified.countDown();
            await(allowCommit);
        }));
        try {
            assertTrue(modified.await(5, TimeUnit.SECONDS));

            // when: 이전 미리보기 버전으로 확정하려 하면 수정 트랜잭션 종료를 기다린다.
            Future<MatchCreateResponse> confirmation = workers.submit(() -> {
                confirmationAttempted.countDown();
                return matchService.create(request);
            });
            assertTrue(confirmationAttempted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> confirmation.get(200, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            update.get(5, TimeUnit.SECONDS);

            // then: 수정 커밋 이후의 버전·활성 상태를 읽어 확정을 거절하고 기록을 남기지 않는다.
            ExecutionException failure = assertThrows(ExecutionException.class, () -> confirmation.get(5, TimeUnit.SECONDS));
            BusinessException error = assertInstanceOf(BusinessException.class, failure.getCause());
            assertEquals(ErrorCode.PREVIEW_OUTDATED, error.getErrorCode());
            transaction().executeWithoutResult(status -> {
                assertEquals(0L, em.createQuery("select count(m) from Match m", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(p) from MatchParticipant p", Long.class).getSingleResult());
            });
        } finally {
            allowCommit.countDown();
        }
    }

    private void changeCurrentInformation(String change, boolean lockFirst) {
        if (change.equals("name") || change.equals("deleted")) {
            Participant participant = lockFirst ? em.find(Participant.class, participantId, LockModeType.PESSIMISTIC_WRITE)
                    : em.find(Participant.class, participantId);
            if (change.equals("name")) participant.rename("수정된 이름");
            else participant.delete(Instant.now());
        } else {
            GameProfile profile = em.createQuery("select g from GameProfile g where g.participantId = :id", GameProfile.class)
                    .setParameter("id", participantId).getSingleResult();
            if (lockFirst) em.lock(profile, LockModeType.PESSIMISTIC_WRITE);
            if (change.equals("score")) profile.overrideScore(9999);
            else profile.changePreferredPositions(Set.of(Position.LOL_TOP));
        }
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 동기화 시간 초과");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 스레드 중단", exception);
        }
    }
}
