package com.example.teamforge.match.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MatchResultRequest(
        @NotNull @Min(0) @Schema(description = "A팀 경기 스코어", minimum = "0") Integer scoreA,
        @NotNull @Min(0) @Schema(description = "B팀 경기 스코어", minimum = "0") Integer scoreB,
        @Size(max = 2000) @Schema(description = "경기 메모. 생략·null은 빈 문자열로 저장", maxLength = 2000) String memo
) {
}
