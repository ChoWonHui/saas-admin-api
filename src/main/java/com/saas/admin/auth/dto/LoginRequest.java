package com.saas.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(description = "계정 이메일", example = "jsj3216@gmail.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "비밀번호", example = "Admin7!wMuf9Zzhpk")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
