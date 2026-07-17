package com.saas.admin.adminaccount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 내부 직원(플랫폼 관리자) 계정.
 * <p>
 * 업체 사용자(user_account)와 <b>테이블을 분리한다.</b> 식별자 체계가 다르기 때문이다 —
 * 직원은 사번(260001)으로, 업체 사용자는 이메일로 로그인한다.
 * <p>
 * 삭제는 물리 삭제하지 않는다. {@code deleted = 'Y'} 로만 표시한다(누가 언제 지웠는지 함께 남긴다).
 * 사번은 재사용하지 않으므로 유니크 제약과 소프트 삭제가 충돌하지 않는다.
 * <p>
 * 이 테이블은 Hibernate 가 새로 만든다. 기존 테이블과 달리 enum 은 VARCHAR 이므로
 * {@code @JdbcTypeCode(CHAR)} 를 붙이지 않는다. (CLAUDE.md §3)
 */
@Entity
@Table(name = "admin_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

    /** 연속 로그인 실패가 이 횟수에 도달하면 계정을 일시 잠근다. (user_account 와 동일 정책) */
    public static final int MAX_LOGIN_FAIL = 5;
    public static final int LOCK_MINUTES = 15;

    /**
     * 계정 생성·비밀번호 초기화 시 넣는 기본 비밀번호.
     * <p>
     * 공개된 값이므로 <b>이 비밀번호로는 아무것도 할 수 없어야 한다.</b> 그래서 이 값이 들어가는 순간
     * {@code mustChangePassword = 'Y'} 가 함께 켜지고, 본인이 새 비밀번호로 바꾸기 전에는
     * 다른 API 를 쓸 수 없다. (JwtAuthenticationFilter 가 막는다)
     */
    public static final String DEFAULT_PASSWORD = "exprism1234!";

    /**
     * 사번이 곧 기본키다. 로그인 ID 이자 다른 테이블이 이 사람을 가리키는 유일한 수단이다.
     * YY + 4자리 순번 (260001 = 2026년 1번째).
     * <p>
     * <b>두 가지가 전제다. 이게 깨지면 참조 무결성이 깨진다.</b>
     * <ol>
     *   <li>발급 후 절대 바뀌지 않는다 (updatable = false — Hibernate 가 UPDATE 문에 싣지 않는다)</li>
     *   <li>재사용하지 않는다 — 퇴사자 사번을 새 사람에게 다시 주면,
     *       그 사람 앞으로 쌓인 감사 기록이 새 사람에게 붙는다.
     *       소프트 삭제(is_deleted='Y')로 행을 남기는 이유가 이것이다.</li>
     * </ol>
     */
    @Id
    @Column(name = "emp_no", nullable = false, length = 6, updatable = false)
    private String empNo;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 연락용. 로그인에 쓰지 않으므로 유니크가 아니고, 없어도 된다. */
    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    /** 부서 (예: 플랫폼개발팀). 조직 개편이 잦으므로 코드가 아니라 자유 입력으로 둔다. */
    @Column(name = "department", length = 50)
    private String department;

    /** 직급 (예: 사원/대리/과장). */
    @Column(name = "job_grade", length = 30)
    private String jobGrade;

    /** 직책 (예: 팀장/파트장). 직급과 달리 보직이다 — 없을 수 있다. */
    @Column(name = "job_title", length = 30)
    private String jobTitle;

    /**
     * 소속 조직 ID. null 이면 아직 어느 조직에도 배치되지 않은 상태다(조직도에서 "미배치").
     * FK 를 걸지 않고 Long 으로 둔다 — 조직 삭제/이동과 느슨하게 결합해, 조직 쪽 변경이
     * 관리자 저장을 깨지 않게 한다. 정합성(배치/해제)은 서비스에서 관리한다.
     */
    @Column(name = "org_id")
    private Long orgId;

    /**
     * 슈퍼관리자 여부. 'Y' / 'N'.
     * <p>
     * 슈퍼는 메뉴 권한 규칙을 <b>우회</b>해 항상 전 메뉴에 접근한다. 권한을 잘못 설정해
     * 아무도 권한관리 화면에 못 들어가는 "자물쇠에 자기가 갇히는" 사고를 막는 안전장치다.
     * 최초 부트스트랩 관리자에게만 부여한다.
     */
    @Column(name = "is_super", nullable = false, length = 1)
    private String isSuper;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdminStatus status;

    /** 소프트 삭제 표시. 'Y' / 'N'. */
    @Column(name = "is_deleted", nullable = false, length = 1)
    private String deleted;

    /**
     * 다음 로그인 때 비밀번호를 반드시 바꿔야 하는가. 'Y' / 'N'.
     * 기본 비밀번호(exprism1234!)가 들어간 계정은 항상 'Y' 다.
     */
    @Column(name = "must_change_password", nullable = false, length = 1)
    private String mustChangePassword;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 삭제를 실행한 관리자의 사번. */
    @Column(name = "deleted_by", length = 6)
    private String deletedBy;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * @param mustChange 기본 비밀번호로 만든 계정이면 true. 본인이 첫 로그인 때 반드시 바꿔야 한다.
     */
    public static AdminAccount create(String empNo, String passwordHash, String name, String email, String phone,
                                      String department, String jobGrade, String jobTitle,
                                      boolean mustChange) {
        AdminAccount admin = new AdminAccount();
        admin.empNo = empNo;   // 채번된 사번. 이후 다시는 바뀌지 않는다.
        admin.passwordHash = passwordHash;
        admin.name = name;
        admin.email = email;
        admin.phone = phone;
        admin.department = department;
        admin.jobGrade = jobGrade;
        admin.jobTitle = jobTitle;
        admin.isSuper = "N";
        admin.status = AdminStatus.ACTIVE;
        admin.deleted = "N";
        admin.mustChangePassword = mustChange ? "Y" : "N";
        admin.loginFailCount = 0;
        admin.passwordChangedAt = LocalDateTime.now();
        return admin;
    }

    public boolean isDeleted() {
        return "Y".equals(deleted);
    }

    public boolean mustChangePassword() {
        return "Y".equals(mustChangePassword);
    }

    /** 잠금 시간이 지났으면 스스로 풀린다. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /** 로그인할 수 있는 상태인가. 삭제된 계정은 상태와 무관하게 로그인할 수 없다. */
    public boolean isUsable() {
        return !isDeleted() && status == AdminStatus.ACTIVE;
    }

    public void onLoginSuccess() {
        this.loginFailCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = LocalDateTime.now();
        if (this.status == AdminStatus.LOCKED) {
            this.status = AdminStatus.ACTIVE;
        }
    }

    public void onLoginFail() {
        this.loginFailCount++;
        if (this.loginFailCount >= MAX_LOGIN_FAIL) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            this.loginFailCount = 0;
        }
    }

    public void update(String name, String email, String phone,
                       String department, String jobGrade, String jobTitle, AdminStatus status) {
        if (name != null) this.name = name;
        if (email != null) this.email = email.isBlank() ? null : email;
        if (phone != null) this.phone = phone.isBlank() ? null : phone;
        if (department != null) this.department = department.isBlank() ? null : department;
        if (jobGrade != null) this.jobGrade = jobGrade.isBlank() ? null : jobGrade;
        if (jobTitle != null) this.jobTitle = jobTitle.isBlank() ? null : jobTitle;
        if (status != null) this.status = status;
    }

    /**
     * 관리자가 남의 비밀번호를 <b>기본값으로 초기화</b>한다.
     * 초기화된 비밀번호는 공개된 값이므로, 본인이 바꾸기 전에는 아무것도 못 하게 플래그를 켠다.
     */
    public void resetPasswordToDefault(String defaultPasswordHash) {
        this.passwordHash = defaultPasswordHash;
        this.mustChangePassword = "Y";
        this.passwordChangedAt = LocalDateTime.now();
        unlock();
    }

    /** 본인이 새 비밀번호로 바꾼다. 여기서만 강제 변경 플래그가 꺼진다. */
    public void changePasswordBySelf(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = "N";
        this.passwordChangedAt = LocalDateTime.now();
        unlock();
    }

    /** 비밀번호를 새로 정했으면 잠금도 함께 푼다. 잠긴 사람을 구제하는 수단이 이것이다. */
    private void unlock() {
        this.loginFailCount = 0;
        this.lockedUntil = null;
        if (this.status == AdminStatus.LOCKED) {
            this.status = AdminStatus.ACTIVE;
        }
    }

    public void softDelete(String actorEmpNo) {
        this.deleted = "Y";
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actorEmpNo;
    }

    /** 조직에 배치한다. null 이면 미배치로 되돌린다. */
    public void assignOrg(Long orgId) {
        this.orgId = orgId;
    }

    /**
     * 조직 배치에 맞춰 부서 라벨을 동기화한다. (배치된 조직의 부서코드에 대응하는 이름)
     * null/빈값이면 부서 없음 — 미배치로 되돌릴 때 쓴다.
     */
    public void syncDepartment(String department) {
        this.department = (department == null || department.isBlank()) ? null : department;
    }

    public boolean isSuper() {
        return "Y".equals(isSuper);
    }

    public void grantSuper() {
        this.isSuper = "Y";
    }
}
