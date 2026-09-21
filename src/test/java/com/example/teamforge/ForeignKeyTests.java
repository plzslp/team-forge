package com.example.teamforge;

import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.GameProfile;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.entity.Position;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fk-test;DB_CLOSE_DELAY=-1")
@Transactional
class ForeignKeyTests {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseRejectsProfileForMissingParticipant() {
        // given: DB에 없는 참여자 ID를 준비한다.
        UUID missingId = UUID.randomUUID();

        // when / then: 애플리케이션 검증을 우회한 SQL도 FK에 의해 거절된다.
        var error = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into game_profiles (id, participant_id, game, base_score, version) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), missingId, "LOL", 1000, 0));
        assertTrue(error.getMostSpecificCause().getMessage().toLowerCase()
                .contains("fk_game_profiles_participant"));
    }

    @Test
    void databaseRejectsSnapshotForMissingParticipant() {
        // given: 정상 내전과 DB에 없는 참여자 ID를 준비한다.
        var match = persistMatch();
        UUID missingId = UUID.randomUUID();

        // when / then: 다른 값이 유효해도 존재하지 않는 참여자의 기록은 거절된다.
        var error = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into match_participants (id, match_id, participant_id, name, score, assigned_position, team) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), match.getId(), missingId, "미등록", 1000, "LOL_TOP", "A"));
        assertTrue(error.getMostSpecificCause().getMessage().toLowerCase()
                .contains("fk_match_participants_participant"));
    }

    @Test
    void profileReferenceBlocksPhysicalParticipantDeletion() {
        // given: 프로필이 연결된 참여자를 저장한다.
        var participant = persistParticipant("참여자");
        em.persist(new GameProfile(participant.getId(), Game.LOL, null, 1000, null, Set.of(Position.LOL_TOP)));
        em.flush();

        // when / then: 프로필이 참조하는 참여자의 물리 삭제는 거절된다.
        var error = assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("delete from participants where id = ?", participant.getId()));
        assertTrue(error.getMostSpecificCause().getMessage().toLowerCase()
                .contains("fk_game_profiles_participant"));
    }

    @Test
    void matchReferenceBlocksPhysicalParticipantDeletion() {
        // given: 프로필 없이 내전 기록만 연결된 참여자를 준비한다.
        var match = persistMatch();
        UUID participantId = match.getParticipants().get(0).getParticipantId();

        // when / then: 과거 내전이 참조하는 참여자의 물리 삭제도 거절된다.
        var error = assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("delete from participants where id = ?", participantId));
        assertTrue(error.getMostSpecificCause().getMessage().toLowerCase()
                .contains("fk_match_participants_participant"));
    }

    @Test
    void softDeletePreservesProfileAndMatchSnapshot() {
        // given: 내전 기록과 프로필이 연결된 참여자를 준비한다.
        var match = persistMatch();
        var snapshot = match.getParticipants().get(0);
        var participant = em.find(Participant.class, snapshot.getParticipantId());
        var profile = new GameProfile(participant.getId(), Game.LOL, null, 1000, null, Set.of(Position.LOL_TOP));
        em.persist(profile);
        em.flush();

        // when: 이름을 변경하고 Soft Delete한 뒤 DB에서 다시 읽는다.
        participant.rename("변경된 이름").delete(Instant.EPOCH);
        em.flush();
        em.clear();

        // then: 참여자·프로필·내전 기록은 남고 확정 당시 이름과 점수도 유지된다.
        assertFalse(em.find(Participant.class, participant.getId()).active());
        assertNotNull(em.find(GameProfile.class, profile.getId()));
        var saved = em.find(Match.class, match.getId()).getParticipants().stream()
                .filter(p -> p.getParticipantId().equals(participant.getId())).findFirst().orElseThrow();
        assertEquals(snapshot.getName(), saved.getName());
        assertEquals(snapshot.getScore(), saved.getScore());
    }

    private Participant persistParticipant(String name) {
        var participant = Participant.register(name);
        em.persist(participant);
        return participant;
    }

    private Match persistMatch() {
        var players = IntStream.range(0, 10).mapToObj(i -> {
            var participant = persistParticipant("참여자" + i);
            return new MatchParticipant(participant.getId(), participant.getName(), 1000,
                    Position.LOL_TOP, i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B);
        }).toList();
        var match = new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, players, null);
        em.persist(match);
        em.flush();
        return match;
    }
}
