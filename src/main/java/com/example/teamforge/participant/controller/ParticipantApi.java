package com.example.teamforge.participant.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.participant.dto.ParticipantCreateRequest;
import com.example.teamforge.participant.dto.ParticipantResponse;
import com.example.teamforge.participant.dto.ParticipantUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/** 참여자 API 계약과 Swagger 문서용 어노테이션을 정의한다. */
@Tag(name = "Participants", description = "참여자 관리 API")
public abstract class ParticipantApi {

    @Operation(summary = "참여자 등록", description = "앞뒤 공백을 제거한 1~20자 이름으로 참여자를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료. Location 헤더에 상세 조회 경로를 반환합니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 이름",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public abstract ResponseEntity<ParticipantResponse> register(@Valid @RequestBody ParticipantCreateRequest request);

    @Operation(summary = "참여자 목록 조회", description = "삭제되지 않은 참여자를 이름, ID 오름차순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 완료. 참여자가 없으면 빈 배열을 반환합니다.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public abstract ResponseEntity<List<ParticipantResponse>> findAll();

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
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public abstract ResponseEntity<ParticipantResponse> findById(@PathVariable("id") UUID id);

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
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public abstract ResponseEntity<ParticipantResponse> update(@PathVariable("id") UUID id,
                                                               @Valid @RequestBody ParticipantUpdateRequest request);

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
    @DeleteMapping("/{id}")
    public abstract ResponseEntity<Void> delete(@PathVariable("id") UUID id);
}
