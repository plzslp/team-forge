package com.example.teamforge.match.dto;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

/** 미리보기 당시 참여자와 해당 게임 프로필의 독립적인 JPA 버전. */
public record ParticipantVersions(long participantVersion, long profileVersion) {
    public ParticipantVersions {
        if (participantVersion < 0 || profileVersion < 0) {
            throw new BusinessException(ErrorCode.INVALID_PREVIEW_VERSION);
        }
    }
}
