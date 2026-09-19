package com.example.teamforge.match.dto;

/** 미리보기 당시 참여자와 해당 게임 프로필의 독립적인 JPA 버전. */
public record ParticipantVersions(long participantVersion, long profileVersion) {
    public ParticipantVersions {
        if (participantVersion < 0 || profileVersion < 0) {
            throw new IllegalArgumentException("참여자와 프로필 버전은 음수일 수 없습니다.");
        }
    }
}
