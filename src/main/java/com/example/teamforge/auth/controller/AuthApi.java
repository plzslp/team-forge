package com.example.teamforge.auth.controller;

import com.example.teamforge.auth.dto.response.AuthMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

@Tag(name = "Auth", description = "공유 계정 인증 API")
public abstract class AuthApi {
    @Operation(summary = "현재 로그인 계정 확인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 로그인 계정"),
            @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content)
    })
    public abstract ResponseEntity<AuthMeResponse> me(Principal principal);

    @Operation(summary = "변경 요청용 CSRF 쿠키 준비",
            description = "XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더에 전달합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "CSRF 쿠키 준비 완료", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content)
    })
    public abstract ResponseEntity<Void> csrf();
}
