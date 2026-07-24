package com.saas.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 업체(사장님/직원) 로그인 요청 — 업체코드 + 아이디 + 비밀번호.
 * 아이디는 '가게 안에서만' 유일하므로 반드시 업체코드와 함께 온다.
 */
public record TenantLoginRequest(

        @Schema(description = "업체코드", example = "A7K2M9QX3P")
        @NotBlank(message = "업체코드는 필수입니다.")
        @Size(max = 20)
        String tenantCode,

        @Schema(description = "로그인 아이디", example = "master")
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 50)
        String loginId,

        @Schema(description = "비밀번호")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
