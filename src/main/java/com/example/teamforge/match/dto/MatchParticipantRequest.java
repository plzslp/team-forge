package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.entity.MatchParticipant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** 이름과 점수는 입력받지 않고 서버의 최신 정보로 확정한다. */
public record MatchParticipantRequest(
        @NotNull UUID participantId,
        @NotNull MatchParticipant.Team team,
        @NotNull @Schema(description = "수동 교체를 반영한 최종 배정 포지션") Position assignedPosition
) {
}
