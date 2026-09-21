package com.example.teamforge.common.exception;

import com.example.teamforge.participant.entity.Participant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalExceptionHandlerTests.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.TestController.class})
class GlobalExceptionHandlerTests {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "invalid-name, 400, INVALID_PARTICIPANT_NAME",
            "deleted, 409, PARTICIPANT_DELETED",
            "integrity, 409, DATA_CONFLICT",
            "optimistic, 409, CONCURRENT_MODIFICATION",
            "unexpected, 500, INTERNAL_SERVER_ERROR"
    })
    void exceptionsUseStableResponseWithoutInternalDetails(String scenario, int status, ErrorCode code) throws Exception {
        // given: 업무 오류·저장 충돌·예상하지 못한 오류를 발생시키는 엔드포인트를 선택한다.
        String path = "/test/errors/" + scenario;

        // when: HTTP 요청으로 예외를 발생시킨다.
        var result = mvc.perform(get(path));

        // then: 상태와 공개 코드·메시지만 반환한다.
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").value(code.getMessage()))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(content().string(not(containsString("private-detail"))));
    }

    @ParameterizedTest
    @CsvSource({"'{', INVALID_REQUEST", "'{\"name\":\"\"}', INVALID_REQUEST", "'{}', INVALID_REQUEST"})
    void invalidJsonAndBeanValidationUseCommonResponse(String body, String code) throws Exception {
        // given: 잘못된 JSON 또는 필수 입력값이 누락된 요청을 준비한다.
        // when: JSON 요청을 전송한다.
        var result = mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body));

        // then: 상세 파싱·검증 메시지를 노출하지 않고 400을 반환한다.
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        // given: POST만 지원하는 경로를 선택한다.
        // when: GET으로 호출한다.
        var result = mvc.perform(get("/test/body"));

        // then: 405와 Allow 헤더를 유지한다.
        result.andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        // given: JSON 대신 텍스트 요청을 준비한다.
        // when: JSON 엔드포인트로 전송한다.
        var result = mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("text"));

        // then: 415 응답을 공통 형식으로 반환한다.
        result.andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void missingParameterAndTypeMismatchReturn400() throws Exception {
        // given: 필수 파라미터 누락과 잘못된 숫자 입력을 준비한다.
        // when / then: 두 요청 모두 400과 같은 응답 형식을 사용한다.
        for (String path : new String[]{"/test/number", "/test/number?value=invalid"}) {
            mvc.perform(get(path)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void missingRouteReturns404() throws Exception {
        // given: 존재하지 않는 경로를 선택한다.
        // when: 해당 경로를 호출한다.
        var result = mvc.perform(get("/missing-route"));

        // then: 404를 500으로 바꾸지 않는다.
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void validRequestIsNotChangedByAdvice() throws Exception {
        // given: 유효한 이름을 준비한다.
        // when: 정상 요청을 보낸다.
        var result = mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참여자\"}"));

        // then: 원래의 정상 응답을 유지한다.
        result.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("참여자"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    // 테스트 소스에만 존재하며 운영 API로 등록되지 않는다.
    @RestController
    static class TestController {
        @GetMapping("/test/errors/{scenario}")
        void fail(@PathVariable String scenario) {
            switch (scenario) {
                case "invalid-name" -> Participant.register(" ");
                case "deleted" -> Participant.register("참여자").delete(Instant.EPOCH).rename("수정");
                case "integrity" -> throw new DataIntegrityViolationException("private-detail: SQL constraint");
                case "optimistic" -> throw new OptimisticLockingFailureException("private-detail: entity version");
                default -> throw new IllegalStateException("private-detail: internal failure");
            }
        }

        @PostMapping(value = "/test/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> body(@Valid @RequestBody NameRequest request) {
            return Map.of("name", request.name());
        }

        @GetMapping("/test/number")
        int number(@RequestParam int value) {
            return value;
        }
    }

    record NameRequest(@NotBlank String name) {}
}
