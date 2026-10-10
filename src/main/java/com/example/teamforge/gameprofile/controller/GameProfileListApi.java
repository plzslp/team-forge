package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.common.exception.ErrorResponse;
import com.example.teamforge.gameprofile.dto.GameProfileListRequest;
import com.example.teamforge.gameprofile.dto.GameProfilePageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "GameProfiles", description = "참여자 게임 프로필 관리 API")
public abstract class GameProfileListApi {
    @Operation(summary = "게임별 참여자 목록 조회", description = "선택한 게임의 프로필이 있는 활성 참여자를 "
            + "이름·참여자 ID 오름차순으로 조회합니다. game은 필수이며 keyword는 이름 부분 검색(대소문자 무시), "
            + "position은 해당 게임의 선호 포지션 필터입니다. page 기본값은 0, size 기본값은 20(1~100)입니다. "
            + "페이지 기본값은 생략 시에만 적용하고 빈 값·잘못된 범위·int 범위를 넘는 offset은 400입니다. "
            + "마지막 페이지 이후는 빈 content와 필터 기준의 전체 개수를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료. 목록과 페이지 정보 반환"),
            @ApiResponse(responseCode = "400", description = "필수 게임 누락 또는 잘못된 검색·포지션·페이징 입력",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public abstract ResponseEntity<GameProfilePageResponse> findAll(@ParameterObject GameProfileListRequest request);
}
