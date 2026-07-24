package com.saas.admin.tenant.dto;

import com.saas.admin.tenant.domain.BranchTable;
import com.saas.admin.tenant.domain.TenantBranch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 업체 지점(호점) 요청·응답 DTO. */
public final class BranchDtos {

    private BranchDtos() {
    }

    @Schema(description = "지점(호점) 추가. 호점 번호는 서버가 자동 채번한다.")
    public record BranchCreateRequest(
            @Schema(description = "지점명(선택). 예: 강남점") @Size(max = 100) String name,
            @Schema(description = "지점 담당자") @Size(max = 50) String managerName,
            @Schema(description = "연락처") @Size(max = 20) String contactPhone,
            @Schema(description = "우편번호") @Size(max = 10) String postalCode,
            @Schema(description = "주소") @Size(max = 255) String address,
            @Schema(description = "상세주소") @Size(max = 255) String addressDetail
    ) {
    }

    @Schema(description = "지점 수정.")
    public record BranchUpdateRequest(
            @Size(max = 100) String name,
            @Size(max = 50) String managerName,
            @Size(max = 20) String contactPhone,
            @Size(max = 10) String postalCode,
            @Size(max = 255) String address,
            @Size(max = 255) String addressDetail
    ) {
    }

    public record BranchResponse(
            Long branchId, Long tenantId, int branchNo, String name, String managerName,
            String contactPhone, String postalCode, String address, String addressDetail,
            boolean takeoutOnly, int floorCount, LocalDateTime createdAt
    ) {
        public static BranchResponse from(TenantBranch b) {
            return new BranchResponse(b.getId(), b.getTenantId(), b.getBranchNo(), b.getName(),
                    b.getManagerName(), b.getContactPhone(), b.getPostalCode(), b.getAddress(),
                    b.getAddressDetail(), b.isTakeoutOnly(), b.getFloorCount(), b.getCreatedAt());
        }
    }

    // ===== 영업장 테이블 배치 =====

    @Schema(description = "테이블/룸 하나. 좌표·크기는 편집기 캔버스 픽셀. code 는 QR 주문 토큰(불변).")
    public record TableDto(
            Long tableId, String code, int floorNo, String label, int seats, String kind,
            int x, int y, int width, int height
    ) {
        public static TableDto from(BranchTable t) {
            return new TableDto(t.getId(), t.getCode(), t.getFloorNo(), t.getLabel(), t.getSeats(), t.getKind(),
                    t.getX(), t.getY(), t.getWidth(), t.getHeight());
        }
    }

    @Schema(description = "지점 영업장 배치 응답.")
    public record LayoutResponse(boolean takeoutOnly, int floorCount, int canvasW, int canvasH, List<TableDto> tables) {
    }

    @Schema(description = "지점 영업장 배치 저장(통째 교체).")
    public record LayoutSaveRequest(
            boolean takeoutOnly,
            int floorCount,
            int canvasW,
            int canvasH,
            List<TableSaveDto> tables
    ) {
        public record TableSaveDto(String code, int floorNo, String label, int seats, String kind,
                                   int x, int y, int width, int height) {
        }
    }
}
