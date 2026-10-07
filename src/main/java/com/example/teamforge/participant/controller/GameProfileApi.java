package com.example.teamforge.participant.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.participant.dto.GameProfileRequest;
import com.example.teamforge.participant.dto.GameProfileResponse;
import com.example.teamforge.participant.entity.Game;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/** Swagger 문서와 메서드 선언. HTTP 매핑과 입력 검증은 Controller에서 처리한다. */
@Tag(name = "GameProfiles", description = "참여자 게임 프로필 관리 API")
public abstract class GameProfileApi {
    @Operation(summary = "게임 프로필 등록·전체 수정", description = "참여자·게임별 하나의 프로필을 관리합니다. "
            + "계정·수동 점수·선호 포지션을 교체하며 환산 점수는 유지합니다. "
            + "신규 환산 점수는 0이며 수동 점수 null은 보정 해제, 0은 유효한 지정값입니다. "
            + "계정은 앞뒤 공백을 제거하고 빈 값은 null로 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신규 등록. Location에 조회 경로를 반환합니다."),
            @ApiResponse(responseCode = "200", description = "기존 프로필 수정"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 또는 게임에 맞지 않는 포지션",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참여자가 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "삭제된 참여자 또는 동시 변경 충돌",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<GameProfileResponse> upsert(UUID participantId, Game game, GameProfileRequest request);

    @Operation(summary = "게임 프로필 조회", description = "활성 참여자의 해당 게임 프로필과 실제 적용 점수를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 UUID 또는 게임",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참여자·프로필이 없거나 참여자가 삭제됨",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<GameProfileResponse> findByParticipantIdAndGame(UUID participantId, Game game);
}
