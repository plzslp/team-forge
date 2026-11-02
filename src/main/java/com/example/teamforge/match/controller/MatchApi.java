package com.example.teamforge.match.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.match.dto.request.MatchCreateRequest;
import com.example.teamforge.match.dto.response.MatchCreateResponse;
import com.example.teamforge.match.dto.request.MatchListRequest;
import com.example.teamforge.match.dto.response.MatchPageResponse;
import com.example.teamforge.match.dto.response.MatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;

import java.util.UUID;

@Tag(name = "Matches", description = "확정 내전 기록 API")
public abstract class MatchApi {
    @Operation(summary = "팀 편성 확정·저장", description = "미리보기의 참가자·프로필 버전과 최종 5:5 팀·포지션을 전달합니다. "
            + "수동 교체는 허용하지만 게임별 역할 구성과 비선호 배정 허용 여부를 다시 검증합니다. "
            + "이름·점수·생성 시각은 서버에서 결정하며 확정 당시 스냅샷을 저장합니다. "
            + "미리보기 이후 참가자·프로필이 바뀌었거나 삭제됐다면 409로 거절하고 재생성을 안내합니다. "
            + "동일 요청을 다시 보내면 별도 경기로 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "경기 생성 완료. 경기 ID·시각·확정 스냅샷과 상세 조회 Location 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·중복 참가자·버전 집합·팀별 역할 구성",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "오래된 미리보기·사용 불가 참가자·비선호 배정 미허용·잠금 충돌",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<MatchCreateResponse> create(MatchCreateRequest request);

    @Operation(summary = "경기 기록 목록 조회", description = "필수 game의 확정 기록을 생성 시각·ID 내림차순으로 조회합니다. "
            + "page 기본 0, size 기본 20(1~100)이며 정렬 변경은 지원하지 않습니다. "
            + "빈 페이징 값·잘못된 범위·int 범위를 넘는 offset은 400입니다. 마지막 페이지 이후는 빈 content와 전체 개수를 반환합니다. "
            + "팀 정보는 확정 당시 스냅샷으로, 원본 참가자의 수정·삭제 여부와 무관하게 유지합니다. 경기 결과 미입력은 null입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "경기 목록과 페이지 정보"),
            @ApiResponse(responseCode = "400", description = "필수 게임 누락 또는 잘못된 게임·페이징 값",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<MatchPageResponse> findAll(@ParameterObject MatchListRequest request);

    @Operation(summary = "경기 기록 상세 조회", description = "확정 당시 A/B팀·개인 및 팀 점수·배정 포지션과 입력된 경기 결과를 조회합니다. "
            + "결과 미입력(null)과 0:0 무승부를 구분합니다. 원본 참가자가 수정되거나 삭제돼도 과거 기록은 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "경기 상세 스냅샷과 결과"),
            @ApiResponse(responseCode = "400", description = "잘못된 경기 UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "경기 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<MatchResponse> findById(UUID id);
}
