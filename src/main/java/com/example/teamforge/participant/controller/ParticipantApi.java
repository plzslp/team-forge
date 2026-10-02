package com.example.teamforge.participant.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.participant.dto.ParticipantCreateRequest;
import com.example.teamforge.participant.dto.ParticipantPageRequest;
import com.example.teamforge.participant.dto.ParticipantResponse;
import com.example.teamforge.participant.dto.ParticipantPageResponse;
import com.example.teamforge.participant.dto.ParticipantUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/** 참여자 API의 메서드 선언과 Swagger 문서를 정의한다. HTTP 매핑과 입력 검증은 Controller에서 처리한다. */
@Tag(name = "Participants", description = "참여자 관리 API")
public abstract class ParticipantApi {

    @Operation(summary = "참여자 등록", description = "앞뒤 공백을 제거한 1~20자 이름으로 참여자를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료. Location 헤더에 상세 조회 경로를 반환합니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 이름",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<ParticipantResponse> register(ParticipantCreateRequest request);

    @Operation(summary = "참여자 목록 조회", description = "삭제되지 않은 참여자를 이름, ID 오름차순으로 페이징 조회합니다. "
            + "page는 0부터 시작하고 기본값은 0, size는 1~100이며 기본값은 20입니다. "
            + "기본값은 파라미터 생략 시에만 적용하며 빈 값은 400을 반환합니다. "
            + "마지막 페이지를 넘으면 content는 빈 배열이며, page × size가 int 범위를 넘으면 400을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료. 목록과 페이지 정보를 반환합니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 번호 또는 크기",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<ParticipantPageResponse> findAll(
            @ParameterObject ParticipantPageRequest request);

    @Operation(summary = "참여자 상세 조회", description = "삭제되지 않은 참여자의 ID와 이름을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참여자가 없거나 삭제됨",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<ParticipantResponse> findById(UUID id);

    @Operation(summary = "참여자 이름 수정", description = "앞뒤 공백을 제거한 1~20자 이름으로 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청, 이름 또는 UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참여자가 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "삭제된 참여자 또는 동시 수정 충돌",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<ParticipantResponse> update(UUID id, ParticipantUpdateRequest request);

    @Operation(summary = "참여자 삭제", description = "Soft Delete로 처리하여 과거 기록을 보존합니다. 이미 삭제된 참여자도 204를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 완료", content = @Content),
            @ApiResponse(responseCode = "400", description = "잘못된 UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "참여자가 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "동시 수정 충돌",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<Void> delete(UUID id);
}
