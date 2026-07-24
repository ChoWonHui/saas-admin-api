package com.saas.admin.tenant.table;

import com.saas.admin.tenant.domain.BranchTable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 업체 콘솔 테이블 관리 DTO. */
public final class TenantTableDtos {

    private TenantTableDtos() {
    }

    public record TableView(Long tableId, String code, String label, int seats, String kind) {
        public static TableView from(BranchTable t) {
            return new TableView(t.getId(), t.getCode(), t.getLabel(), t.getSeats(), t.getKind());
        }
    }

    @Schema(description = "테이블 생성 (이름은 비우면 순번으로 자동)")
    public record TableCreateRequest(
            @Size(max = 30) String label,
            @Schema(description = "좌석 수", example = "4")
            @Min(1) @Max(30) int seats,
            @Schema(description = "TABLE 또는 ROOM", example = "TABLE") String kind
    ) {
    }
}
