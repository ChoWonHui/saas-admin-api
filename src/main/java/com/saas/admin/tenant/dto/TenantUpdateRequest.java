package com.saas.admin.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 업체 정보 수정. slug·code·status 는 바꾸지 않는다 (slug 는 URL 이라 불변, 상태는 개설/중지로 관리).
 */
public record TenantUpdateRequest(

        @Schema(description = "업체명", example = "맛있는식당")
        @NotBlank(message = "업체명은 필수입니다.")
        @Size(max = 100, message = "업체명은 100자를 넘을 수 없습니다.")
        String tenantName,

        @Schema(description = "경로(slug). 바꾸면 형식·예약어·중복을 검증한다. null/미변경이면 그대로.", example = "delicious")
        @Size(min = 3, max = 30, message = "경로는 3~30자여야 합니다.")
        String tenantSlug,

        @Schema(description = "요금제 ID (선택, null 이면 해제)", example = "2")
        Long planId,

        @Schema(description = "대표자명") @Size(max = 50) String ownerName,
        @Schema(description = "사업자등록번호") @Size(max = 20) String businessNo,
        @Schema(description = "연락처") @Size(max = 20) String contactPhone,

        @Schema(description = "연락 이메일")
        @Email(message = "연락 이메일 형식이 올바르지 않습니다.")
        @Size(max = 150) String contactEmail,

        @Schema(description = "우편번호") @Size(max = 10) String postalCode,
        @Schema(description = "주소") @Size(max = 255) String address,
        @Schema(description = "상세주소") @Size(max = 255) String addressDetail
) {
}
