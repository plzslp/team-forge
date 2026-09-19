package com.example.teamforge;

import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.Position;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.entity.GameProfile;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.entity.MatchResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:model-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Transactional
class JpaModelTests {
    @Autowired EntityManager em;

    @Test
    void profileUpdatesAndSoftDeleteArePersisted() {
        // given: 참여자와 수동 점수 1500점인 프로필을 DB에 저장한다.
        var participant = Participant.register("친구");
        var profile = new GameProfile(participant.getId(), Game.LOL, null, 0, 1500, Set.of(Position.LOL_TOP, Position.LOL_MID));
        em.persist(participant);
        em.persist(profile);
        em.flush();
        em.clear();

        var loaded = em.find(GameProfile.class, profile.getId());
        long previousVersion = loaded.getVersion();
        // when: 환산 점수를 갱신하고 참여자를 논리 삭제한 뒤 DB에 반영한다.
        loaded.refreshScore(2000);
        em.find(Participant.class, participant.getId()).delete(Instant.EPOCH);
        em.flush();
        em.clear();

        // then: DB에서 다시 조회해 수동 점수·포지션·버전·삭제 상태를 확인한다.
        loaded = em.find(GameProfile.class, profile.getId());
        assertEquals(1500, loaded.effectiveScore());
        assertEquals(Set.of(Position.LOL_TOP, Position.LOL_MID), loaded.getPreferredPositions());
        assertTrue(loaded.getVersion() > previousVersion);
        assertFalse(em.find(Participant.class, participant.getId()).active());
        // when: 수동 보정을 해제하고 DB에 반영한다.
        loaded.overrideScore(null);
        em.flush();
        em.clear();
        // then: 다시 조회하면 최신 환산 점수가 적용된다.
        assertEquals(2000, em.find(GameProfile.class, profile.getId()).effectiveScore());
    }

    @Test
    void matchCascadesSnapshotsAndEmbedsOptionalResult() {
        // given: 참가자 10명과 경기 결과가 없는 내전을 준비한다.
        var players = IntStream.range(0, 10).mapToObj(i -> new MatchParticipant(
                UUID.randomUUID(), "참가자" + i, 1000, Position.LOL_TOP,
                i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B)).toList();
        var match = new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, players, null);
        // when: 내전을 저장하고 영속성 컨텍스트를 비운다.
        em.persist(match);
        em.flush();
        em.clear();

        // then: 참가자도 함께 저장되고 경기 결과는 비어 있다.
        var loaded = em.find(Match.class, match.getId());
        assertEquals(10, loaded.getParticipants().size());
        assertTrue(loaded.getParticipants().stream()
                .allMatch(player -> player.getAssignedPosition() == Position.LOL_TOP));
        assertNull(loaded.getResult());
        // when: 경기 결과를 기록하고 DB에 반영한다.
        loaded.recordResult(new MatchResult(2, 1, "첫 경기"));
        em.flush();
        em.clear();
        // then: 재조회한 결과·메모와 기존 참가자 점수가 유지된다.
        loaded = em.find(Match.class, match.getId());
        assertEquals("첫 경기", loaded.getResult().getMemo());
        assertEquals(MatchResult.Outcome.WIN, loaded.getResult().outcomeFor(MatchParticipant.Team.A));
        assertEquals(1000, loaded.getParticipants().get(0).getScore());
    }

    @Test
    void overwatchPositionsAreStoredAsEnumNames() {
        // given: 오버워치 프로필과 5:5 편성을 준비한다.
        var participant = Participant.register("오버워치 참가자");
        var profile = new GameProfile(participant.getId(), Game.OVERWATCH, null, 1000, null,
                Set.of(Position.OVERWATCH_SUPPORT));
        var players = IntStream.range(0, 10).mapToObj(i -> new MatchParticipant(
                i == 0 ? participant.getId() : UUID.randomUUID(), "참가자" + i, 1000,
                Position.OVERWATCH_SUPPORT, i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B)).toList();
        var match = new Match(UUID.randomUUID(), Game.OVERWATCH, Instant.EPOCH, players, null);

        // when: 저장 후 영속성 컨텍스트를 비운다.
        em.persist(participant);
        em.persist(profile);
        em.persist(match);
        em.flush();
        em.clear();

        // then: enum으로 복원되며 DB에는 순번 대신 이름이 저장된다.
        assertEquals(Set.of(Position.OVERWATCH_SUPPORT),
                em.find(GameProfile.class, profile.getId()).getPreferredPositions());
        assertTrue(em.find(Match.class, match.getId()).getParticipants().stream()
                .allMatch(player -> player.getAssignedPosition() == Position.OVERWATCH_SUPPORT));
        assertEquals("OVERWATCH_SUPPORT", em.createNativeQuery(
                "select position from game_profile_positions where profile_id = :id", String.class)
                .setParameter("id", profile.getId()).getSingleResult());
        assertEquals("OVERWATCH_SUPPORT", em.createNativeQuery(
                "select assigned_position from match_participants where match_id = :id", String.class)
                .setParameter("id", match.getId()).getResultList().get(0));
    }
}
