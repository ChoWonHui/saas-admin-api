package com.saas.admin.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 업체 정보 수정. code·status 는 바꾸지 않는다 (코드는 불변, 상태는 개설/중지로 관리).
 */
public record TenantUpdateRequest(

        @Schema(description = "업체명", example = "맛있는식당")
        @NotBlank(message = "업체명은 필수입니다.")
        @Size(max = 100, message = "업체명은 100자를 넘을 수 없습니다.")
        String tenantName,

        @Schema(description = "요금제 ID (선택, null 이면 해제)", example = "2")
        Long planId,

        @Schema(description = "대표자명") @Size(max = 50) String ownerName,
        @Schema(description = "사업자등록번호") @Size(max = 20) String businessNo,
        @Schema(description = "통신판매업 신고번호") @Size(max = 30) String mailOrderSalesNo,
        @Schema(description = "연락처") @Size(max = 20) String contactPhone,

        @Schema(description = "연락 이메일")
        @Email(message = "연락 이메일 형식이 올바르지 않습니다.")
        @Size(max = 150) String contactEmail,

        @Schema(description = "우편번호") @Size(max = 10) String postalCode,
        @Schema(description = "주소") @Size(max = 255) String address,
        @Schema(description = "상세주소") @Size(max = 255) String addressDetail
) {
}
