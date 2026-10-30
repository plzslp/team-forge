package com.example.teamforge.match.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.match.dto.MatchCreateRequest;
import com.example.teamforge.match.dto.MatchCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Matches", description = "확정 내전 기록 API")
public abstract class MatchApi {
    @Operation(summary = "팀 편성 확정·저장", description = "미리보기의 참가자·프로필 버전과 최종 5:5 팀·포지션을 전달합니다. "
            + "수동 교체는 허용하지만 게임별 역할 구성과 비선호 배정 허용 여부를 다시 검증합니다. "
            + "이름·점수·생성 시각은 서버에서 결정하며 확정 당시 스냅샷을 저장합니다. "
            + "미리보기 이후 참가자·프로필이 바뀌었거나 삭제됐다면 409로 거절하고 재생성을 안내합니다. "
            + "동일 요청을 다시 보내면 별도 경기로 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "경기 생성 완료. 경기 ID·시각·확정 스냅샷 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·중복 참가자·버전 집합·팀별 역할 구성",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "오래된 미리보기·사용 불가 참가자·비선호 배정 미허용·잠금 충돌",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<MatchCreateResponse> create(MatchCreateRequest request);
}
