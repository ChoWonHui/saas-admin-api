package com.saas.admin.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 업체 등록 요청. 업체와 대표 계정을 한 번에 만든다. (설계안 §14 — 플랫폼 관리자 MVP)
 * slug 는 형식 규칙과 예약어 블랙리스트를 서비스에서 다시 검증한다.
 */
public record CreateTenantRequest(

        @Schema(description = "업체명. 한글을 써도 된다.", example = "맛있는식당")
        @NotBlank(message = "업체명은 필수입니다.")
        @Size(max = 100, message = "업체명은 100자를 넘을 수 없습니다.")
        String tenantName,

        @Schema(
                description = """
                        URL 경로로 쓰인다 (`https://.../{tenantSlug}`).
                        영소문자·숫자·하이픈만, 3~30자. 하이픈으로 시작·종료하거나 연속(`--`)될 수 없다.
                        한글은 쓸 수 없고, `admin`·`api` 같은 예약어 37건도 막힌다.
                        """,
                example = "delicious")
        @NotBlank(message = "경로(slug)는 필수입니다.")
        @Size(min = 3, max = 30, message = "경로는 3~30자여야 합니다.")
        String tenantSlug,

        @Schema(description = "요금제 ID (1=FREE, 2=BASIC, 3=PRO)", example = "2")
        @NotNull(message = "요금제는 필수입니다.")
        Long planId,

        @Schema(description = "대표자명", example = "홍길동")
        @Size(max = 50) String ownerName,

        @Schema(description = "사업자등록번호", example = "123-45-67890")
        @Size(max = 20) String businessNo,

        @Schema(description = "업체 연락처", example = "02-1234-5678")
        @Size(max = 20) String contactPhone,

        @Schema(description = "업체 연락 이메일", example = "shop@delicious.com")
        @Email(message = "업체 연락 이메일 형식이 올바르지 않습니다.")
        @Size(max = 150)
        String contactEmail,

        // --- 대표 계정 (이 업체의 TENANT_OWNER 로 함께 생성된다) ---

        @Schema(description = "대표 계정 로그인 이메일. 전역에서 유일해야 한다.", example = "owner@delicious.com")
        @NotBlank(message = "대표 계정 이메일은 필수입니다.")
        @Email(message = "대표 계정 이메일 형식이 올바르지 않습니다.")
        @Size(max = 150)
        String ownerEmail,

        @Schema(description = "대표 계정 비밀번호 (8자 이상)", example = "Owner1234!")
        @NotBlank(message = "대표 계정 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.")
        String ownerPassword,

        @Schema(description = "대표 계정 이름", example = "홍길동")
        @NotBlank(message = "대표 계정 이름은 필수입니다.")
        @Size(max = 50)
        String ownerAccountName
) {
}
