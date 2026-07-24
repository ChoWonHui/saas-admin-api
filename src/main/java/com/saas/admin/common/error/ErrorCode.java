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
    LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "업체를 찾을 수 없습니다."),
    TENANT_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 업체입니다."),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "지점(호점)을 찾을 수 없습니다."),
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다."),
    STAFF_ROLE_INVALID(HttpStatus.BAD_REQUEST, "직원 역할은 대표·매니저·직원 중에서 선택하세요."),
    TENANT_OWNER_EXISTS(HttpStatus.CONFLICT, "이미 대표 계정이 있습니다. 대표는 업체당 1명입니다."),
    CANNOT_DELETE_OWNER(HttpStatus.CONFLICT, "대표 계정은 삭제할 수 없습니다. 먼저 다른 사람에게 대표를 넘기세요."),
    MENU_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "메뉴 분류를 찾을 수 없습니다."),
    MENU_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "요금제를 찾을 수 없습니다."),
    TENANT_CODE_GENERATION_FAILED(HttpStatus.CONFLICT, "업체 코드 생성에 실패했습니다. 다시 시도하세요."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),

    // 콘솔 메뉴
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."),
    MENU_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "상위 메뉴를 찾을 수 없습니다."),
    MENU_DEPTH_EXCEEDED(HttpStatus.CONFLICT, "메뉴는 2단계까지만 만들 수 있습니다."),
    MENU_INVALID_PARENT(HttpStatus.CONFLICT, "자기 자신이나 자기 하위 메뉴를 상위 메뉴로 지정할 수 없습니다."),
    MENU_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 메뉴가 있어 삭제할 수 없습니다. 하위 메뉴를 먼저 삭제하세요."),
    MENU_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 위치에 같은 이름의 메뉴가 이미 있습니다."),

    // 메뉴 권한
    PERM_NOT_SUBSET(HttpStatus.CONFLICT, "직책 권한은 부서가 허용한 메뉴 중에서만 줄 수 있습니다. 부서 권한을 먼저 넓히세요."),

    // 달력
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    CALENDAR_WRITE_DENIED(HttpStatus.FORBIDDEN, "이 범위의 일정을 작성할 권한이 없습니다."),
    CALENDAR_NOT_OWNER(HttpStatus.FORBIDDEN, "본인이 작성한 일정만 수정·삭제할 수 있습니다."),
    CALENDAR_NO_TEAM(HttpStatus.CONFLICT, "소속 팀이 없어 팀별 일정을 만들 수 없습니다. 조직에 먼저 배치되어야 합니다."),
    CAL_PERM_NOT_SUBSET(HttpStatus.CONFLICT, "직책 달력 권한은 부서가 허용한 범위 중에서만 줄 수 있습니다."),

    // 공통코드
    CODE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "코드 그룹을 찾을 수 없습니다."),
    CODE_GROUP_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 그룹코드입니다."),
    CODE_GROUP_HAS_CODES(HttpStatus.CONFLICT, "코드가 남아 있어 그룹을 삭제할 수 없습니다. 코드를 먼저 삭제하세요."),
    CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "코드를 찾을 수 없습니다."),
    CODE_DUPLICATED(HttpStatus.CONFLICT, "이 그룹에 이미 있는 코드값입니다."),

    // 조직 / 조직도
    ORG_NOT_FOUND(HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다."),
    ORG_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "상위 조직을 찾을 수 없습니다."),
    ORG_CYCLE(HttpStatus.CONFLICT, "자기 자신이나 하위 조직을 상위로 지정할 수 없습니다."),
    ORG_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 조직이 있어 삭제할 수 없습니다. 하위 조직을 먼저 옮기거나 삭제하세요."),
    ORG_HAS_MEMBERS(HttpStatus.CONFLICT, "소속된 사원이 있어 삭제할 수 없습니다. 사원을 먼저 다른 조직으로 옮기세요."),
    LEADER_NOT_IN_ORG(HttpStatus.CONFLICT, "부서장은 그 조직에 소속된 사원이어야 합니다."),
    ORG_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 다른 조직이 쓰는 부서코드입니다."),
    ORG_CODE_NOT_REGISTERED(HttpStatus.CONFLICT, "공통코드(부서)에 등록되지 않은 부서코드입니다. 공통코드에서 먼저 등록하세요."),

    // 공지사항
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),
    NOTICE_FORBIDDEN(HttpStatus.FORBIDDEN, "작성자 본인만 수정·삭제할 수 있습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "작성자 본인만 수정·삭제할 수 있습니다."),

    // 파일 업로드 (공지 에디터 이미지)
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "빈 파일입니다."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다. 이미지 파일만 올릴 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다. 최대 5MB 까지 올릴 수 있습니다."),
    FILE_STORAGE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소가 비활성화되어 있습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    IMAGE_SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 검색이 설정되지 않았습니다."),
    IMAGE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "이미지 검색에 실패했습니다."),

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
