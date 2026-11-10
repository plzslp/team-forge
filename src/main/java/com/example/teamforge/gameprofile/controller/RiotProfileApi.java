package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.gameprofile.dto.response.GameProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "GameProfiles")
public abstract class RiotProfileApi {
    @Operation(summary = "롤 솔로랭크 갱신", description = "저장된 Riot ID(이름#태그)로 한국 서버의 솔로랭크를 조회합니다. "
            + "정상 조회된 언랭크는 환산 점수 0이며 수동 지정 점수는 유지합니다. "
            + "조회 중 프로필이 수정됐으면 409로 거절합니다. 외부 조회 실패 시 기존 점수를 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "갱신한 프로필과 Riot 랭크"),
            @ApiResponse(responseCode = "400", description = "잘못된 UUID 또는 저장된 Riot ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참가자·롤 프로필·Riot 계정 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "조회 중 참가자·프로필 변경 또는 삭제",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Riot 통신 실패·잘못된 응답·시간 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "API 키 설정·인증 오류 또는 호출 제한",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<GameProfileResponse> refresh(UUID participantId);
}
