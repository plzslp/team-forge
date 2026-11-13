package com.example.teamforge.participant.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticipantEntityTests {
    @Test
    void softDeletePreservesIdentityAndName() {
        // given: 이름 앞뒤에 공백이 있는 참여자를 준비한다.
        Participant participant = Participant.register("  친구  ");
        // when: 참여자를 논리 삭제한다.
        Participant deleted = participant.delete(Instant.EPOCH);
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

    private void assertBusinessError(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }
}
