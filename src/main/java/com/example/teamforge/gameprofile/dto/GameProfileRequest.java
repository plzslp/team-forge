package com.example.teamforge.gameprofile.dto;

import com.example.teamforge.gameprofile.entity.Position;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record GameProfileRequest(
        @Size(max = 255)
        @Schema(description = "게임 계정. 생략 또는 null이면 연결 해제") String accountId,
        @PositiveOrZero
        @Schema(description = "수동 지정 점수. null이면 보정 해제, 0도 유효") Integer manualScore,
        @NotEmpty
        @Schema(description = "해당 게임의 선호 포지션. 하나 이상 선택")
        Set<@NotNull Position> preferredPositions) {

    public GameProfileRequest {
        if (accountId != null) {
            accountId = accountId.strip();
            if (accountId.isEmpty()) accountId = null;
        }
    }
}
