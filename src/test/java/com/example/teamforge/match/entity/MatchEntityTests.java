package com.example.teamforge.match.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.teamforge.match.dto.TeamPreview;
import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.Position;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MatchEntityTests {
    private List<MatchParticipant> participants() {
        return IntStream.range(0, 10).mapToObj(i -> new MatchParticipant(
                UUID.randomUUID(), "참가자" + i, 1000, Position.LOL_TOP,
                i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B)).toList();
    }

    @Test
    void invalidTeamSizeIsRejected() {
        // given: 참가자가 없는 목록을 입력한다.
        // when / then: 내전을 생성하면 인원 검증에서 예외가 발생한다.
        assertThrows(IllegalArgumentException.class, () ->
                new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, List.of(), null));
    }

    @Test
    void duplicateParticipantsAreRejected() {
        // given: 동일 참여자가 중복된 목록을 준비한다.
        var players = new ArrayList<>(participants());
        players.set(1, players.get(0));
        // when / then: 내전을 생성하면 중복 검증에서 예외가 발생한다.
        assertThrows(IllegalArgumentException.class, () ->
                new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, players, null));
    }

    @Test
    void snapshotIsCopiedAndResultIsSeparateFromScore() {
        // given: A/B팀에 5명씩 배정한 참가자 목록을 준비한다.
        var players = new ArrayList<>(participants());
        // when: 내전을 생성한 뒤 원본 목록을 비운다.
        var match = new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, players, null);
        players.clear();
        // then: 복사된 참가자 목록은 유지되고 경기 결과는 아직 없다.
        assertEquals(10, match.getParticipants().size());
        assertNull(match.getResult());
        // when: A팀 2점, B팀 1점으로 경기 결과를 기록한다.
        var recorded = match.recordResult(new MatchResult(2, 1, "첫 경기"));
        // then: 팀별 승패가 계산된다.
        assertEquals(MatchResult.Outcome.WIN, recorded.getResult().outcomeFor(MatchParticipant.Team.A));
        assertEquals(MatchResult.Outcome.LOSS, recorded.getResult().outcomeFor(MatchParticipant.Team.B));
        // when / then: 동점 결과는 무승부로 판정한다.
        assertEquals(MatchResult.Outcome.DRAW, new MatchResult(1, 1, null).outcomeFor(MatchParticipant.Team.A));
        // when / then: 반환된 참가자 목록은 외부에서 변경할 수 없다.
        assertThrows(UnsupportedOperationException.class, () -> match.getParticipants().clear());
    }

    @Test
    void previewRequiresVersionsForEveryParticipant() {
        // given: 참가자 10명과 비어 있는 버전 맵을 입력한다.
        // when / then: 미리보기를 생성하면 버전 누락으로 예외가 발생한다.
        assertThrows(IllegalArgumentException.class, () ->
                new TeamPreview(Game.LOL, participants(), Map.of()));
    }
}
