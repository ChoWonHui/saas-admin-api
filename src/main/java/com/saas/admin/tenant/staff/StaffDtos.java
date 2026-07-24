package com.saas.admin.tenant.staff;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 업체 직원(로그인 계정) 관리 DTO. */
public final class StaffDtos {

    private StaffDtos() {
    }

    public record StaffResponse(
            Long tenantUserId, Long userId, String loginId, String email, String name, String phone,
            Integer roleId, String roleName, String status, LocalDateTime lastLoginAt
    ) {
    }

    @Schema(description = "직원 계정 생성. 로그인은 '업체코드 + 아이디 + 비밀번호'. 아이디는 가게 안에서만 유일하면 된다.")
    public record StaffCreateRequest(
            @Schema(description = "로그인 아이디(가게 안에서 유일)", example = "master")
            @NotBlank(message = "로그인 아이디는 필수입니다.")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                    message = "아이디는 영문/숫자/._- 조합 3~50자입니다.")
            String loginId,

            @Schema(description = "이메일(선택). 비밀번호 재설정 등에 쓰인다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 150) String email,

            @NotBlank(message = "초기 비밀번호는 필수입니다.")
            @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.")
            String password,

            @NotBlank(message = "이름은 필수입니다.") @Size(max = 50) String name,
            @Size(max = 20) String phone,

            @Schema(description = "역할 2=대표 3=매니저 4=직원", example = "4")
            @NotNull(message = "역할은 필수입니다.") Integer roleId
    ) {
    }

    @Schema(description = "직원 정보 수정.")
    public record StaffUpdateRequest(
            @NotBlank(message = "이름은 필수입니다.") @Size(max = 50) String name,
            @Size(max = 20) String phone,
            @NotNull Integer roleId,
            @Schema(description = "ACTIVE / SUSPENDED") String status
    ) {
    }

    @Schema(description = "비밀번호 재설정.")
    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.") String newPassword) {
    }
}
