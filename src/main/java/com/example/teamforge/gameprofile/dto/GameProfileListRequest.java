package com.example.teamforge.gameprofile.dto;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.Position;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameProfileListRequest {
    @NotNull
    @Schema(description = "조회할 게임", requiredMode = Schema.RequiredMode.REQUIRED)
    private Game game;

    @Size(max = 20)
    @Schema(description = "참여자 이름 부분 검색. 앞뒤 공백 제거 후 빈 값은 전체 조회")
    private String keyword;

    @Schema(description = "선호 포지션 필터. 선택한 게임의 포지션만 허용")
    private Position position;

    @NotNull
    @Min(0)
    @Schema(description = "0부터 시작하는 페이지 번호", defaultValue = "0", minimum = "0")
    private Integer page = 0;

    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "페이지 크기", defaultValue = "20", minimum = "1", maximum = "100")
    private Integer size = 20;

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? null : keyword.strip();
        if (this.keyword != null && this.keyword.isEmpty()) this.keyword = null;
    }

    @AssertTrue
    @Schema(hidden = true)
    public boolean isPositionCompatible() {
        return game == null || position == null || position.supports(game);
    }
}
