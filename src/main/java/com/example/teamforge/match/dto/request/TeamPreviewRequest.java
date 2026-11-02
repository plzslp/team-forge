package com.example.teamforge.match.dto.request;

import com.example.teamforge.gameprofile.entity.Game;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TeamPreviewRequest(
        @NotNull Game game,
        @NotNull @Size(min = 10, max = 10)
        @Schema(description = "중복 없는 활성 참가자 10명의 ID") List<@NotNull UUID> participantIds,
        @Schema(description = "선호 포지션만으로 편성할 수 없을 때 비선호 배정 허용. 생략·null은 false", defaultValue = "false")
        Boolean allowNonPreferredPositions
) {
    public TeamPreviewRequest {
        allowNonPreferredPositions = Boolean.TRUE.equals(allowNonPreferredPositions);
    }
}
