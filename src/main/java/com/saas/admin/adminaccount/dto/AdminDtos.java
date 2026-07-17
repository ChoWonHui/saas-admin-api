package com.saas.admin.adminaccount.dto;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.domain.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 계정 관련 요청/응답 DTO 모음. 하나하나가 파일이 될 만큼 크지 않아 한곳에 둔다. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // --- 인증 ---

    @Schema(description = "관리자 로그인. 이메일이 아니라 사번으로 한다.")
    public record AdminLoginRequest(
            @Schema(description = "사번 (YY + 4자리)", example = "260001")
            @NotBlank(message = "사번은 필수입니다.")
            String empNo,

            @Schema(description = "비밀번호")
            @NotBlank(message = "비밀번호는 필수입니다.")
            String password
    ) {
    }

    public record AdminRefreshRequest(
            @NotBlank(message = "리프레시 토큰은 필수입니다.")
            String refreshToken
    ) {
    }

    public record AdminTokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String empNo,
            String name,
            @Schema(description = "true 면 비밀번호를 먼저 바꿔야 한다. 이 토큰으로는 비밀번호 변경 외 아무것도 못 한다.")
            boolean mustChangePassword
    ) {
    }

    @Schema(description = "본인 비밀번호 변경. 기본 비밀번호로 로그인한 사람이 첫 화면에서 호출한다.")
    public record ChangeMyPasswordRequest(
            @Schema(description = "새 비밀번호. 최소 8자.")
            @NotBlank(message = "새 비밀번호는 필수입니다.")
            @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
            String newPassword,

            @Schema(description = "새 비밀번호 확인. 위 값과 정확히 같아야 한다.")
            @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
            String confirmPassword
    ) {
    }

    @Schema(description = "현재 로그인한 관리자. 콘솔 헤더 표시용 — 이름은 토큰이 아니라 DB 에서 읽는다(수정 즉시 반영).")
    public record AdminMeResponse(
            String empNo,
            String name,
            String email,
            @Schema(description = "true 면 비밀번호를 먼저 바꿔야 한다. 토큰 클레임 기준 — 서버 권한 판정과 같은 값을 쓴다.")
            boolean mustChangePassword,
            @Schema(description = "슈퍼관리자면 true. 메뉴 권한 규칙을 우회하고 권한관리 화면을 쓸 수 있다.")
            boolean isSuper
    ) {
        public static AdminMeResponse of(AdminAccount admin, boolean mustChangePassword) {
            return new AdminMeResponse(admin.getEmpNo(), admin.getName(), admin.getEmail(),
                    mustChangePassword, admin.isSuper());
        }
    }

    // --- CRUD ---

    @Schema(description = """
            관리자 생성. **사번과 비밀번호를 보내지 않는다.**
            사번은 서버가 채번하고, 비밀번호는 기본값(exprism1234!)으로 시작한다.
            본인이 첫 로그인 때 반드시 새 비밀번호로 바꿔야 한다.
            """)
    public record CreateAdminRequest(
            @Schema(example = "김철수")
            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String name,

            @Schema(description = "연락용 이메일. 로그인에는 쓰지 않는다.", example = "chulsoo@example.com")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email,

            @Schema(example = "010-1234-5678")
            @Size(max = 20, message = "전화번호는 20자를 넘을 수 없습니다.")
            String phone,

            @Schema(description = "부서", example = "플랫폼개발팀")
            @Size(max = 50, message = "부서는 50자를 넘을 수 없습니다.")
            String department,

            @Schema(description = "직급", example = "대리")
            @Size(max = 30, message = "직급은 30자를 넘을 수 없습니다.")
            String jobGrade,

            @Schema(description = "직책 (보직 — 없을 수 있다)", example = "파트장")
            @Size(max = 30, message = "직책은 30자를 넘을 수 없습니다.")
            String jobTitle
    ) {
    }

    @Schema(description = "관리자 수정. 보낸 항목만 바뀐다. 사번과 비밀번호는 여기서 바꿀 수 없다.")
    public record UpdateAdminRequest(
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String name,

            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email,

            @Size(max = 20, message = "전화번호는 20자를 넘을 수 없습니다.")
            String phone,

            @Size(max = 50, message = "부서는 50자를 넘을 수 없습니다.")
            String department,

            @Size(max = 30, message = "직급은 30자를 넘을 수 없습니다.")
            String jobGrade,

            @Size(max = 30, message = "직책은 30자를 넘을 수 없습니다.")
            String jobTitle,

            @Schema(description = "ACTIVE / LOCKED / DISABLED")
            AdminStatus status
    ) {
    }

    public record AdminResponse(
            String empNo,
            String name,
            String email,
            String phone,
            String department,
            String jobGrade,
            String jobTitle,
            @Schema(description = "소속 조직 ID. 미배치면 null.")
            Long orgId,
            AdminStatus status,
            boolean deleted,
            LocalDateTime deletedAt,
            /** 퇴사처리를 실행한 관리자의 사번. */
            String deletedBy,
            boolean locked,
            @Schema(description = "true 면 기본 비밀번호 상태다. 본인이 아직 비밀번호를 바꾸지 않았다.")
            boolean mustChangePassword,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
        public static AdminResponse from(AdminAccount admin) {
            return new AdminResponse(
                    admin.getEmpNo(),
                    admin.getName(),
                    admin.getEmail(),
                    admin.getPhone(),
                    admin.getDepartment(),
                    admin.getJobGrade(),
                    admin.getJobTitle(),
                    admin.getOrgId(),
                    admin.getStatus(),
                    admin.isDeleted(),
                    admin.getDeletedAt(),
                    admin.getDeletedBy(),
                    admin.isLocked(),
                    admin.mustChangePassword(),
                    admin.getLastLoginAt(),
                    admin.getCreatedAt());
        }

        public static List<AdminResponse> from(List<AdminAccount> admins) {
            return admins.stream().map(AdminResponse::from).toList();
        }
    }
}
