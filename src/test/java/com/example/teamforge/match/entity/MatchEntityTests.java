package com.example.teamforge.match.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.teamforge.match.dto.TeamPreview;
import com.example.teamforge.match.dto.ParticipantVersions;
import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.Position;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MatchEntityTests {
    private List<MatchParticipant> participants() {
        return IntStream.range(0, 10).mapToObj(i -> new MatchParticipant(
                UUID.randomUUID(), "참가자" + i, 1000, Position.LOL_TOP,
                i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B)).toList();
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void matchAndPreviewRejectPositionsFromAnotherGame(Game game) {
        // given: 5:5 구성 중 한 명에게 다른 게임의 포지션을 배정한다.
        Position valid = game == Game.LOL ? Position.LOL_TOP : Position.OVERWATCH_TANK;
        Position invalid = game == Game.LOL ? Position.OVERWATCH_SUPPORT : Position.LOL_SUPPORT;
        var players = IntStream.range(0, 10).mapToObj(i -> new MatchParticipant(
                UUID.randomUUID(), "참가자" + i, 1000, i == 9 ? invalid : valid,
                i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B)).toList();
        var versions = versionsFor(players);

        // when / then: 확정 기록과 미리보기 모두 게임 불일치를 거절한다.
        assertBusinessError(ErrorCode.POSITION_GAME_MISMATCH, () ->
                new Match(UUID.randomUUID(), game, Instant.EPOCH, players, null));
        assertBusinessError(ErrorCode.POSITION_GAME_MISMATCH, () -> new TeamPreview(game, players, versions));
    }

    private Map<UUID, ParticipantVersions> versionsFor(List<MatchParticipant> players) {
        var versions = new HashMap<UUID, ParticipantVersions>();
        players.forEach(p -> versions.put(p.getParticipantId(), new ParticipantVersions(1, 2)));
        return versions;
    }

    @Test
    void previewPreservesIndependentVersionsAndCopiesVersionMap() {
        // given: 참여자 버전은 같고 프로필 버전만 다른 두 미리보기 입력을 준비한다.
        var players = participants();
        UUID id = players.get(0).getParticipantId();
        var original = versionsFor(players);
        var changed = new HashMap<>(original);
        changed.put(id, new ParticipantVersions(1, 3));

        // when: 두 미리보기를 생성한 뒤 원본 맵을 비운다.
        var before = new TeamPreview(Game.LOL, players, original);
        var after = new TeamPreview(Game.LOL, players, changed);
        original.clear();

        // then: 두 버전을 독립적으로 보존하고 외부 수정으로부터 보호한다.
        assertEquals(new ParticipantVersions(1, 2), before.participantVersions().get(id));
        assertEquals(new ParticipantVersions(1, 3), after.participantVersions().get(id));
        assertThrows(UnsupportedOperationException.class, () -> before.participantVersions().clear());
    }

    @Test
    void previewRejectsUnexpectedParticipantVersion() {
        // given: 필요한 버전 정보 외에 다른 참여자의 버전이 포함되어 있다.
        var players = participants();
        var versions = versionsFor(players);
        versions.put(UUID.randomUUID(), new ParticipantVersions(0, 0));

        // when / then: 참가자 구성과 일치하지 않는 버전 맵을 거절한다.
        assertBusinessError(ErrorCode.PREVIEW_PARTICIPANTS_MISMATCH, () -> new TeamPreview(Game.LOL, players, versions));
    }

    @Test
    void invalidTeamSizeIsRejected() {
        // given: 참가자가 없는 목록을 입력한다.
        // when / then: 내전을 생성하면 인원 검증에서 예외가 발생한다.
        assertBusinessError(ErrorCode.INVALID_TEAM_COMPOSITION, () ->
                new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, List.of(), null));
    }

    @Test
    void duplicateParticipantsAreRejected() {
        // given: 동일 참여자가 중복된 목록을 준비한다.
        var players = new ArrayList<>(participants());
        players.set(1, players.get(0));
        // when / then: 내전을 생성하면 중복 검증에서 예외가 발생한다.
        assertBusinessError(ErrorCode.INVALID_TEAM_COMPOSITION, () ->
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
        assertBusinessError(ErrorCode.PREVIEW_PARTICIPANTS_MISMATCH, () ->
                new TeamPreview(Game.LOL, participants(), Map.of()));
    }
    @Test
    void invalidResultAndSnapshotUseSpecificErrorCodes() {
        // given: 잘못된 경기 점수, 메모와 스냅샷 입력을 준비한다.
        String oversizedMemo = "x".repeat(2001);

        // when / then: 각 업무 규칙에 해당하는 오류 코드를 반환한다.
        assertBusinessError(ErrorCode.INVALID_MATCH_SCORE, () -> new MatchResult(-1, 0, null));
        assertBusinessError(ErrorCode.INVALID_MATCH_MEMO, () -> new MatchResult(0, 0, oversizedMemo));
        assertBusinessError(ErrorCode.INVALID_MATCH_PARTICIPANT, () ->
                new MatchParticipant(UUID.randomUUID(), "", 0, Position.LOL_TOP, MatchParticipant.Team.A));
        assertBusinessError(ErrorCode.INVALID_PREVIEW_VERSION, () -> new ParticipantVersions(-1, 0));
        assertBusinessError(ErrorCode.INVALID_PREVIEW_VERSION, () -> new ParticipantVersions(0, -1));
    }

    private void assertBusinessError(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }
}
