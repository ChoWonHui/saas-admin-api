package com.saas.admin.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 인증 / 인가
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도하세요."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    NOT_A_MEMBER(HttpStatus.FORBIDDEN, "해당 업체에 소속되어 있지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // 관리자 계정 (내부 직원 — 사번으로 로그인한다)
    INVALID_ADMIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 올바르지 않습니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."),
    ADMIN_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 퇴사처리된 관리자입니다."),
    CANNOT_DELETE_SELF(HttpStatus.CONFLICT, "자기 자신은 퇴사처리할 수 없습니다."),
    CANNOT_DISABLE_LAST_ADMIN(HttpStatus.CONFLICT, "마지막으로 남은 관리자는 퇴사처리하거나 정지할 수 없습니다."),
    EMPLOYEE_NO_EXHAUSTED(HttpStatus.CONFLICT, "올해 발급 가능한 사번을 모두 소진했습니다."),
    EMPLOYEE_NO_GENERATION_FAILED(HttpStatus.CONFLICT, "사번 채번에 실패했습니다. 다시 시도하세요."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인이 일치하지 않습니다."),
    PASSWORD_SAME_AS_DEFAULT(HttpStatus.BAD_REQUEST, "초기 비밀번호와 다른 비밀번호로 변경해야 합니다."),

    // 업체(테넌트)
    SLUG_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "경로(slug) 형식이 올바르지 않습니다."),
    SLUG_RESERVED(HttpStatus.CONFLICT, "플랫폼이 예약한 경로라 사용할 수 없습니다."),
    SLUG_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 경로입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "업체를 찾을 수 없습니다."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "요금제를 찾을 수 없습니다."),
    TENANT_CODE_GENERATION_FAILED(HttpStatus.CONFLICT, "업체 코드 생성에 실패했습니다. 다시 시도하세요."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
