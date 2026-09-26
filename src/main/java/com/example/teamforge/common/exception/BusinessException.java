package com.example.teamforge.common.exception;

import java.util.Objects;
import lombok.Getter;

/** 예상 가능한 업무 규칙 위반. 내부 오류 메시지를 응답에 직접 전달하지 않는다. */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).getMessage());
        this.errorCode = errorCode;
    }

}
