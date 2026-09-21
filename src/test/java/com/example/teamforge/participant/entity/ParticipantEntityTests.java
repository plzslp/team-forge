package com.example.teamforge.participant.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ParticipantEntityTests {
    @Test
    void softDeletePreservesIdentityAndName() {
        // given: 이름 앞뒤에 공백이 있는 참여자를 준비한다.
        var participant = Participant.register("  친구  ");
        // when: 참여자를 논리 삭제한다.
        var deleted = participant.delete(Instant.EPOCH);
        // then: 식별자와 이름은 유지되고 비활성 상태가 된다.
        assertEquals(participant.getId(), deleted.getId());
        assertEquals("친구", deleted.getName());
        assertFalse(deleted.active());
        // when / then: 삭제된 참여자의 이름을 변경하면 예외가 발생한다.
        assertBusinessError(ErrorCode.PARTICIPANT_DELETED, () -> deleted.rename("다른 이름"));
    }

    @Test
    void blankNameIsRejected() {
        // given: 공백으로만 된 이름을 입력한다.
        // when / then: 등록을 시도하면 예외가 발생한다.
        assertBusinessError(ErrorCode.INVALID_PARTICIPANT_NAME, () -> Participant.register("   "));
    }

    @Test
    void manualScoreSurvivesRefreshAndCanBeCleared() {
        // given: 기본 점수 0점, 수동 보정이 없는 프로필을 준비한다.
        var profile = new GameProfile(UUID.randomUUID(), Game.LOL, null, 0, null, Set.of(Position.LOL_TOP, Position.LOL_MID));
        assertEquals(0, profile.effectiveScore());
        // when: 수동 점수를 지정한 뒤 환산 점수를 갱신한다.
        var updated = profile.overrideScore(1500).refreshScore(2000);
        // then: 수동 점수가 우선 적용된다.
        assertEquals(1500, updated.effectiveScore());
        // when / then: 보정을 해제하면 갱신된 환산 점수가 적용된다.
        assertEquals(2000, updated.overrideScore(null).effectiveScore());
        // when / then: 수동으로 지정한 0점도 유효하다.
        assertEquals(0, updated.overrideScore(0).effectiveScore());
    }

    @Test
    void invalidPositionIsRejected() {
        // given: 롤 프로필에 오버워치 포지션인 TANK를 입력한다.
        // when / then: 프로필을 생성하면 예외가 발생한다.
        assertBusinessError(ErrorCode.INVALID_PREFERRED_POSITION, () ->
                new GameProfile(UUID.randomUUID(), Game.LOL, null, 0, null, Set.of(Position.OVERWATCH_TANK)));
    }

    @ParameterizedTest
    @EnumSource(Position.class)
    void positionIsAcceptedOnlyForItsGame(Position position) {
        // given: 각 포지션의 소속 게임과 다른 게임을 준비한다.
        Game game = switch (position) {
            case LOL_TOP, LOL_JUNGLE, LOL_MID, LOL_BOTTOM, LOL_SUPPORT -> Game.LOL;
            case OVERWATCH_TANK, OVERWATCH_DAMAGE, OVERWATCH_SUPPORT -> Game.OVERWATCH;
        };
        Game otherGame = game == Game.LOL ? Game.OVERWATCH : Game.LOL;

        // when: 해당 게임의 프로필을 생성한다.
        var profile = new GameProfile(UUID.randomUUID(), game, null, 0, null, Set.of(position));

        // then: 선택한 포지션이 유지된다.
        assertEquals(Set.of(position), profile.getPreferredPositions());

        // when / then: 다른 게임의 프로필은 해당 포지션을 거절한다.
        assertBusinessError(ErrorCode.INVALID_PREFERRED_POSITION, () ->
                new GameProfile(UUID.randomUUID(), otherGame, null, 0, null, Set.of(position)));
    }

    @Test
    void invalidPositionChangePreservesExistingPositions() {
        // given: 롤 서포터를 선택한 프로필을 준비한다.
        var profile = new GameProfile(UUID.randomUUID(), Game.LOL, null, 0, null,
                Set.of(Position.LOL_SUPPORT));

        // when / then: 다른 게임의 서포터나 빈 목록으로 변경하면 거절하고 기존 값을 유지한다.
        assertBusinessError(ErrorCode.INVALID_PREFERRED_POSITION, () ->
                profile.changePreferredPositions(Set.of(Position.OVERWATCH_SUPPORT)));
        assertBusinessError(ErrorCode.INVALID_PREFERRED_POSITION, () -> profile.changePreferredPositions(Set.of()));
        assertEquals(Set.of(Position.LOL_SUPPORT), profile.getPreferredPositions());
    }
    @Test
    void negativeScoreUsesBusinessErrorAndPreservesScore() {
        // given: 유효한 점수의 프로필을 준비한다.
        var profile = new GameProfile(UUID.randomUUID(), Game.LOL, null, 1000, null, Set.of(Position.LOL_TOP));

        // when / then: 음수 환산·수동 점수를 거절하고 기존 점수를 유지한다.
        assertBusinessError(ErrorCode.INVALID_SCORE, () -> profile.refreshScore(-1));
        assertBusinessError(ErrorCode.INVALID_SCORE, () -> profile.overrideScore(-1));
        assertEquals(1000, profile.effectiveScore());
    }

    private void assertBusinessError(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }
}
