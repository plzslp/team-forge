package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Game;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchListRequest {
    @NotNull
    @Schema(description = "조회할 게임", requiredMode = Schema.RequiredMode.REQUIRED)
    private Game game;

    @NotNull
    @Min(0)
    @Schema(description = "0부터 시작하는 페이지 번호", defaultValue = "0", minimum = "0")
    private Integer page = 0;

    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "페이지 크기", defaultValue = "20", minimum = "1", maximum = "100")
    private Integer size = 20;
}
