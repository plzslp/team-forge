package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Game;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchCreateRequest(
        @NotNull Game game,
        @NotNull @Size(min = 10, max = 10) List<@NotNull @Valid MatchParticipantRequest> participants,
        @NotNull @Size(min = 10, max = 10)
        @Schema(description = "참가자 ID와 미리보기 당시 참여자·프로필 버전. 참가자 집합과 정확히 일치해야 함")
        Map<@NotNull UUID, @NotNull @Valid ParticipantVersions> participantVersions,
        @Schema(description = "수동 교체 후 비선호 배정도 확정할지 여부. 생략·null은 false", defaultValue = "false")
        Boolean allowNonPreferredPositions
) {
    public MatchCreateRequest {
        allowNonPreferredPositions = Boolean.TRUE.equals(allowNonPreferredPositions);
    }
}
