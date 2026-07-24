package com.saas.admin.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 업체 등록(업체 정보만). 대표 로그인 계정은 만들지 않는다 — 필요하면 별도로 생성한다.
 * slug 는 서비스에서 형식·예약어·중복을 다시 검증한다.
 */
public record TenantCreateRequest(

        @Schema(description = "업체명. 한글 가능.", example = "맛있는식당")
        @NotBlank(message = "업체명은 필수입니다.")
        @Size(max = 100, message = "업체명은 100자를 넘을 수 없습니다.")
        String tenantName,

        @Schema(description = "URL 경로(slug). 영소문자·숫자·하이픈, 3~30자.", example = "delicious")
        @NotBlank(message = "경로(slug)는 필수입니다.")
        @Size(min = 3, max = 30, message = "경로는 3~30자여야 합니다.")
        String tenantSlug,

        @Schema(description = "요금제 ID (선택)", example = "2")
        Long planId,

        @Schema(description = "대표자명", example = "홍길동")
        @Size(max = 50) String ownerName,

        @Schema(description = "사업자등록번호", example = "123-45-67890")
        @Size(max = 20) String businessNo,

        @Schema(description = "연락처", example = "02-1234-5678")
        @Size(max = 20) String contactPhone,

        @Schema(description = "연락 이메일", example = "shop@delicious.com")
        @Email(message = "연락 이메일 형식이 올바르지 않습니다.")
        @Size(max = 150) String contactEmail,

        @Schema(description = "우편번호") @Size(max = 10) String postalCode,
        @Schema(description = "주소") @Size(max = 255) String address,
        @Schema(description = "상세주소") @Size(max = 255) String addressDetail
) {
}
