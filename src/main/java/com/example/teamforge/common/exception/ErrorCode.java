package com.example.teamforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 클라이언트가 구분할 수 있는 오류 코드와 공개 메시지. */
@Getter
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이나 입력값을 확인하세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "요청한 응답 형식을 제공할 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "데이터 제약으로 요청을 처리할 수 없습니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "데이터가 변경되었습니다. 새로 조회한 뒤 다시 시도하세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    INVALID_PARTICIPANT_NAME(HttpStatus.BAD_REQUEST, "이름은 공백이 아닌 1~20자여야 합니다."),
    PARTICIPANT_DELETED(HttpStatus.CONFLICT, "삭제된 참여자는 수정할 수 없습니다."),
    INVALID_PREFERRED_POSITION(HttpStatus.BAD_REQUEST, "게임에 맞는 선호 포지션을 하나 이상 선택하세요."),
    INVALID_SCORE(HttpStatus.BAD_REQUEST, "점수는 음수일 수 없습니다."),
    INVALID_TEAM_COMPOSITION(HttpStatus.BAD_REQUEST, "중복 없는 참가자 10명을 팀당 5명으로 구성해야 합니다."),
    POSITION_GAME_MISMATCH(HttpStatus.BAD_REQUEST, "배정 포지션은 내전 게임에 속해야 합니다."),
    INVALID_MATCH_PARTICIPANT(HttpStatus.BAD_REQUEST, "참가자 스냅샷 값이 올바르지 않습니다."),
    INVALID_MATCH_SCORE(HttpStatus.BAD_REQUEST, "경기 스코어는 음수일 수 없습니다."),
    INVALID_MATCH_MEMO(HttpStatus.BAD_REQUEST, "메모는 2000자 이하로 입력하세요."),
    INVALID_PREVIEW_VERSION(HttpStatus.BAD_REQUEST, "참여자와 프로필 버전은 음수일 수 없습니다."),
    PREVIEW_PARTICIPANTS_MISMATCH(HttpStatus.BAD_REQUEST, "참가자별 변경 감지 버전이 필요합니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

}
