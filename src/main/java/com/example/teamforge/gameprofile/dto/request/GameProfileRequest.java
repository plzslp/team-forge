package com.example.teamforge.gameprofile.dto.request;

import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.gameprofile.entity.ManualRank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.Set;

public record GameProfileRequest(
        @Size(max = 255)
        @Schema(description = "게임 계정. 생략 또는 null이면 연결 해제") String accountId,
        @PositiveOrZero
        @Schema(description = "기존 숫자 점수. 0도 유효하며 수동 티어와 함께 보낼 수 없음") Integer manualScore,
        @Valid @Schema(description = "편성용 수동 티어. 수동 점수와 티어 모두 null이면 보정 해제") ManualRank manualRank,
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
