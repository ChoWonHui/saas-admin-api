package com.saas.admin.tenant.table;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.domain.BranchTable;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.domain.TenantBranch;
import com.saas.admin.tenant.TenantBranchService;
import com.saas.admin.tenant.dto.BranchDtos.LayoutResponse;
import com.saas.admin.tenant.dto.BranchDtos.LayoutSaveRequest;
import com.saas.admin.tenant.repository.BranchTableRepository;
import com.saas.admin.tenant.repository.TenantBranchRepository;
import com.saas.admin.tenant.repository.TenantRepository;
import com.saas.admin.tenant.table.TenantTableDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 업체(사장님) 콘솔의 테이블 생성/관리. 로그인한 업체(업체코드)를 키로 자기 가게 테이블만 다룬다.
 * 지점을 따로 고르지 않고 <b>기본 지점(첫 지점, 없으면 자동 생성)</b>에 테이블을 만든다.
 * 각 테이블에는 주문 QR(내URL/업체코드/테이블코드)이 붙는다.
 */
@Service
@RequiredArgsConstructor
public class TenantTableService {

    private final TenantRepository tenantRepository;
    private final TenantBranchRepository branchRepository;
    private final BranchTableRepository tableRepository;
    private final TenantBranchService branchService;

    // ===== 배치 편집기(관리자 콘솔과 동일한 편집기) =====

    /** 기본 지점의 배치도를 반환. 지점이 없으면 자동으로 만든다. */
    @Transactional
    public LayoutResponse getLayout(Long tenantId) {
        TenantBranch branch = defaultBranch(tenantId);
        return branchService.layout(tenantId, branch.getId());
    }

    /** 기본 지점의 배치도를 저장(관리자 콘솔과 같은 upsert 로직). */
    @Transactional
    public LayoutResponse saveLayout(Long tenantId, LayoutSaveRequest req) {
        TenantBranch branch = defaultBranch(tenantId);
        return branchService.saveLayout(tenantId, branch.getId(), req);
    }

    @Transactional(readOnly = true)
    public List<TableView> list(Long tenantId) {
        TenantBranch branch = defaultBranchReadOnly(tenantId);
        if (branch == null) return List.of();
        return tableRepository.findByBranchIdOrderByFloorNoAscIdAsc(branch.getId()).stream()
                .map(TableView::from).toList();
    }

    @Transactional
    public TableView create(Long tenantId, TableCreateRequest req) {
        TenantBranch branch = defaultBranch(tenantId);
        Long branchId = branch.getId();
        List<BranchTable> existing = tableRepository.findByBranchIdOrderByFloorNoAscIdAsc(branchId);
        int n = existing.size();

        String kind = "ROOM".equals(req.kind()) ? "ROOM" : "TABLE";
        int seats = req.seats() <= 0 ? 4 : Math.min(req.seats(), 30);
        int[] wh = sizeFor(seats, kind);
        // 서로 겹치지 않게 계단식 기본 위치 (배치 편집기에서 이어서 옮길 수 있게)
        int x = 20 + (n % 6) * 100;
        int y = 20 + (n / 6) * 110;
        String label = (req.label() == null || req.label().isBlank())
                ? (n + 1) + "번" : req.label().trim();

        BranchTable t = tableRepository.save(BranchTable.of(
                branchId, UUID.randomUUID().toString(), 1, label, seats, kind, x, y, wh[0], wh[1]));
        return TableView.from(t);
    }

    @Transactional
    public void delete(Long tenantId, Long tableId) {
        BranchTable t = requireOwnedTable(tenantId, tableId);
        tableRepository.delete(t);
    }

    /** QR 이미지에 쓸 주문 URL — 내URL/업체코드/테이블코드. */
    @Transactional(readOnly = true)
    public String orderUrl(Long tenantId, Long tableId, String orderBaseUrl) {
        BranchTable t = requireOwnedTable(tenantId, tableId);
        String tenantCode = tenantRepository.findById(tenantId)
                .map(Tenant::getCode)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
        return orderBaseUrl + "/" + tenantCode + "/" + t.getCode();
    }

    // ===== 내부 =====

    /** 이 테이블이 정말 이 업체(기본 지점) 소유인지 확인하고 반환. */
    private BranchTable requireOwnedTable(Long tenantId, Long tableId) {
        BranchTable t = tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND, "테이블을 찾을 수 없습니다."));
        boolean owned = branchRepository.findByTenantIdAndDeletedOrderByBranchNoAsc(tenantId, "N").stream()
                .anyMatch(b -> b.getId().equals(t.getBranchId()));
        if (!owned) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "다른 가게의 테이블은 다룰 수 없습니다.");
        }
        return t;
    }

    private TenantBranch defaultBranchReadOnly(Long tenantId) {
        requireTenant(tenantId);
        return branchRepository.findByTenantIdAndDeletedOrderByBranchNoAsc(tenantId, "N").stream()
                .findFirst().orElse(null);
    }

    /** 기본 지점(첫 지점). 없으면 '1호점'을 자동으로 만든다. */
    private TenantBranch defaultBranch(Long tenantId) {
        requireTenant(tenantId);
        return branchRepository.findByTenantIdAndDeletedOrderByBranchNoAsc(tenantId, "N").stream()
                .findFirst()
                .orElseGet(() -> {
                    int nextNo = branchRepository.maxBranchNo(tenantId) + 1;
                    return branchRepository.save(TenantBranch.create(
                            tenantId, nextNo, null, null, null, null, null, null));
                });
    }

    private void requireTenant(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException(ErrorCode.TENANT_NOT_FOUND);
        }
    }

    /** 좌석 수·종류로 기본 크기(px)를 정한다. 배치 편집기와 톤을 맞춘 대략치. */
    private int[] sizeFor(int seats, String kind) {
        if ("ROOM".equals(kind)) return new int[]{120, 100};
        if (seats <= 2) return new int[]{72, 72};
        if (seats <= 4) return new int[]{92, 72};
        if (seats <= 6) return new int[]{116, 82};
        return new int[]{140, 92};
    }
}
