package com.saas.admin.adminaccount;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.domain.AdminStatus;
import com.saas.admin.adminaccount.dto.AdminDtos.CreateAdminRequest;
import com.saas.admin.adminaccount.dto.AdminDtos.UpdateAdminRequest;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정 CRUD. 사람을 가리키는 키는 <b>사번</b>이다 (ERP 관례).
 * <p>
 * 삭제는 <b>소프트 삭제</b>다 (is_deleted = 'Y'). 물리 삭제하지 않는다 —
 * 사번이 PK 이므로, 행을 지우면 그 사번을 참조하던 기록(토큰·감사 로그)이 허공을 가리킨다.
 * 그리고 사번을 재사용하지 않는 한, 삭제된 행이 남아 있어도 새 사번과 충돌하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final String NOT_DELETED = "N";

    private final AdminAccountRepository adminRepository;
    private final EmployeeNoService employeeNoService;
    private final AdminAuthService adminAuthService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 사번은 서버가 채번하고, 비밀번호는 <b>기본값(exprism1234!)</b>으로 시작한다.
     * 만드는 사람이 비밀번호를 정하지 않는다 — 남의 비밀번호를 아는 사람이 없어야 한다.
     * 본인이 첫 로그인 때 반드시 바꾼다(mustChangePassword = Y).
     * <p>
     * 채번은 트랜잭션 안에서 해야 실패 시 번호도 함께 롤백된다.
     */
    @Transactional
    public AdminAccount create(CreateAdminRequest request) {
        String empNo = employeeNoService.issue();

        return adminRepository.save(AdminAccount.create(
                empNo,
                passwordEncoder.encode(AdminAccount.DEFAULT_PASSWORD),
                request.name(),
                blankToNull(request.email()),
                blankToNull(request.phone()),
                blankToNull(request.department()),
                blankToNull(request.jobGrade()),
                blankToNull(request.jobTitle()),
                true));
    }

    @Transactional(readOnly = true)
    public Page<AdminAccount> list(boolean includeDeleted, Pageable pageable) {
        return includeDeleted
                ? adminRepository.findAll(pageable)
                : adminRepository.findByDeleted(NOT_DELETED, pageable);
    }

    /** 삭제된 관리자도 단건 조회는 된다. 감사 추적용이다. */
    @Transactional(readOnly = true)
    public AdminAccount get(String empNo) {
        return adminRepository.findById(empNo)
                .orElseThrow(() -> new ApiException(ErrorCode.ADMIN_NOT_FOUND));
    }

    @Transactional
    public AdminAccount update(String empNo, UpdateAdminRequest request) {
        AdminAccount admin = getAlive(empNo);

        // 마지막 관리자를 정지시키면 아무도 콘솔에 들어올 수 없다. 삭제와 같은 이유로 막는다.
        if (request.status() != null && request.status() != AdminStatus.ACTIVE && isLastActiveAdmin(admin)) {
            throw new ApiException(ErrorCode.CANNOT_DISABLE_LAST_ADMIN);
        }

        admin.update(request.name(), request.email(), request.phone(),
                request.department(), request.jobGrade(), request.jobTitle(), request.status());

        // 더 이상 로그인할 수 없는 상태로 바꿨다면, 이미 발급된 토큰도 함께 끊는다.
        if (request.status() != null && request.status() != AdminStatus.ACTIVE) {
            adminAuthService.revokeAll(empNo);
        }
        return admin;
    }

    /**
     * 관리자가 남의 비밀번호를 <b>기본값으로 초기화</b>한다. 새 비밀번호를 입력받지 않는다 —
     * 초기화한 사람이 남의 비밀번호를 알고 있으면 안 된다.
     * <p>
     * 초기화된 계정은 mustChangePassword = Y 가 되어, 본인이 새 비밀번호로 바꾸기 전에는
     * 로그인해도 비밀번호 변경 화면 밖으로 나갈 수 없다.
     *
     * @return 안내에 쓸 기본 비밀번호
     */
    @Transactional
    public String resetPassword(String empNo) {
        AdminAccount admin = getAlive(empNo);
        admin.resetPasswordToDefault(passwordEncoder.encode(AdminAccount.DEFAULT_PASSWORD));
        // 비밀번호가 바뀌었으면 기존 세션은 모두 끊는다. (탈취 대응의 기본)
        adminAuthService.revokeAll(empNo);
        return AdminAccount.DEFAULT_PASSWORD;
    }

    /**
     * 본인이 자기 비밀번호를 바꾼다. 여기서만 강제 변경 플래그가 꺼진다.
     * <p>
     * 바꾼 뒤 <b>모든 토큰을 폐기한다.</b> 그래서 바로 들어가지지 않고 다시 로그인해야 한다 —
     * 새 비밀번호를 한 번 더 입력하게 만들어 기억을 굳히는 것이 목적이다.
     */
    @Transactional
    public void changeMyPassword(String empNo, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new ApiException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        // 기본 비밀번호는 공개된 값이다. 그대로 두는 것은 바꾸지 않은 것과 같다.
        if (AdminAccount.DEFAULT_PASSWORD.equals(newPassword)) {
            throw new ApiException(ErrorCode.PASSWORD_SAME_AS_DEFAULT);
        }

        AdminAccount admin = getAlive(empNo);
        admin.changePasswordBySelf(passwordEncoder.encode(newPassword));
        adminAuthService.revokeAll(empNo);
    }

    /**
     * 소프트 삭제. 세 가지를 함께 막고 처리한다.
     * <ol>
     *   <li>자기 자신 삭제 금지 — 실수로 스스로를 지우는 사고가 가장 흔하다</li>
     *   <li>마지막 관리자 삭제 금지 — 아무도 못 들어오는 상태가 된다</li>
     *   <li>리프레시 토큰 전량 폐기 — 안 하면 삭제된 사람이 최대 14일간 계속 쓴다</li>
     * </ol>
     */
    @Transactional
    public void delete(String empNo, String actorEmpNo) {
        if (empNo.equals(actorEmpNo)) {
            throw new ApiException(ErrorCode.CANNOT_DELETE_SELF);
        }

        AdminAccount admin = adminRepository.findById(empNo)
                .orElseThrow(() -> new ApiException(ErrorCode.ADMIN_NOT_FOUND));
        if (admin.isDeleted()) {
            throw new ApiException(ErrorCode.ADMIN_ALREADY_DELETED);
        }
        if (isLastActiveAdmin(admin)) {
            throw new ApiException(ErrorCode.CANNOT_DISABLE_LAST_ADMIN);
        }

        admin.softDelete(actorEmpNo);
        adminAuthService.revokeAll(empNo);
    }

    private AdminAccount getAlive(String empNo) {
        return adminRepository.findByEmpNoAndDeleted(empNo, NOT_DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.ADMIN_NOT_FOUND));
    }

    /** 이 사람이 지금 살아있는 유일한 ACTIVE 관리자인가. */
    private boolean isLastActiveAdmin(AdminAccount admin) {
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            return false;   // 이미 못 쓰는 계정이면 마지막 한 명일 수 없다
        }
        return adminRepository.countByDeletedAndStatus(NOT_DELETED, AdminStatus.ACTIVE) <= 1;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
