package com.example.teamforge.match.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.match.dto.request.TeamPreviewRequest;
import com.example.teamforge.match.dto.response.TeamPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "TeamPreviews", description = "미저장 팀 편성 미리보기 API")
public abstract class TeamPreviewApi {
    @Operation(summary = "팀 편성 미리보기", description = "활성 참가자 10명의 해당 게임 프로필로 5:5 편성을 계산합니다. "
            + "선호 포지션 충족을 우선하고 팀 점수 차이를 최소화합니다. "
            + "롤은 각 포지션 1명, 오버워치는 탱커 1·딜러 2·서포터 2명입니다. "
            + "비선호 배정을 허용하면 위반 인원 수를 최소화한 뒤 점수 차이를 최소화합니다. "
            + "저장하지 않으며 변경 감지 버전은 후속 확정 요청에서 서버가 재검증해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미리보기 생성 완료. 비선호 배정 시 경고 포함"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 또는 중복 참가자",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참가자가 없거나 삭제됨, 해당 게임 프로필 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "선호 포지션만으로 편성 불가능. 명시적 비선호 배정 허용 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<TeamPreviewResponse> create(TeamPreviewRequest request);
}
