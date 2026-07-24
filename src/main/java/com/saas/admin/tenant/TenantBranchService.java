package com.saas.admin.tenant;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.domain.BranchTable;
import com.saas.admin.tenant.domain.TenantBranch;
import com.saas.admin.tenant.dto.BranchDtos.*;
import com.saas.admin.tenant.repository.BranchTableRepository;
import com.saas.admin.tenant.repository.TenantBranchRepository;
import com.saas.admin.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 업체 지점(호점) 관리. 한 업체 아래 1호점·2호점… 을 추가/수정/삭제한다. */
@Service
@RequiredArgsConstructor
public class TenantBranchService {

    private static final String NOT_DELETED = "N";

    private final TenantBranchRepository branchRepository;
    private final BranchTableRepository tableRepository;
    private final TenantRepository tenantRepository;

    /** 업체의 기본 지점 id — 첫 지점, 없으면 '1호점'을 자동 생성. (업체 콘솔의 테이블·메뉴가 공유) */
    @Transactional
    public Long defaultBranchId(Long tenantId) {
        requireTenant(tenantId);
        return branchRepository.findByTenantIdAndDeletedOrderByBranchNoAsc(tenantId, NOT_DELETED).stream()
                .findFirst()
                .map(TenantBranch::getId)
                .orElseGet(() -> {
                    int nextNo = branchRepository.maxBranchNo(tenantId) + 1;
                    return branchRepository.save(TenantBranch.create(
                            tenantId, nextNo, null, null, null, null, null, null)).getId();
                });
    }

    /** 한 업체의 지점 목록(호점 순). */
    @Transactional(readOnly = true)
    public List<BranchResponse> list(Long tenantId) {
        requireTenant(tenantId);
        return branchRepository.findByTenantIdAndDeletedOrderByBranchNoAsc(tenantId, NOT_DELETED).stream()
                .map(BranchResponse::from)
                .toList();
    }

    /** 지점 추가. 호점 번호는 자동 채번(기존 최댓값 + 1). */
    @Transactional
    public BranchResponse create(Long tenantId, BranchCreateRequest req) {
        requireTenant(tenantId);
        int nextNo = branchRepository.maxBranchNo(tenantId) + 1;
        TenantBranch saved = branchRepository.save(TenantBranch.create(
                tenantId, nextNo, req.name(), req.managerName(),
                req.contactPhone(), req.postalCode(), req.address(), req.addressDetail()));
        return BranchResponse.from(saved);
    }

    @Transactional
    public BranchResponse update(Long tenantId, Long branchId, BranchUpdateRequest req) {
        TenantBranch branch = findOrThrow(tenantId, branchId);
        branch.update(req.name(), req.managerName(), req.contactPhone(),
                req.postalCode(), req.address(), req.addressDetail());
        return BranchResponse.from(branch);
    }

    /** 지점 소프트 삭제. 호점 번호는 재사용하지 않는다(다음 채번은 전체 최댓값 기준). */
    @Transactional
    public void delete(Long tenantId, Long branchId) {
        TenantBranch branch = findOrThrow(tenantId, branchId);
        if (branch.isDeleted()) return;
        branch.markDeleted();
    }

    // ===== 영업장 테이블 배치 =====

    /** 지점의 배치도(포장전문점 여부·층수·테이블 목록)를 반환. */
    @Transactional(readOnly = true)
    public LayoutResponse layout(Long tenantId, Long branchId) {
        TenantBranch branch = findOrThrow(tenantId, branchId);
        List<TableDto> tables = tableRepository.findByBranchIdOrderByFloorNoAscIdAsc(branchId).stream()
                .map(TableDto::from)
                .toList();
        // 컬럼 추가 이전 행은 0 일 수 있어 기본값으로 보정한다.
        int cw = branch.getCanvasW() > 0 ? branch.getCanvasW() : 760;
        int ch = branch.getCanvasH() > 0 ? branch.getCanvasH() : 460;
        return new LayoutResponse(branch.isTakeoutOnly(), branch.getFloorCount(), cw, ch, tables);
    }

    /**
     * 배치도 저장. 테이블은 통째로 교체(기존 삭제 후 재삽입)한다.
     * 포장전문점이면 테이블을 비운다.
     */
    @Transactional
    public LayoutResponse saveLayout(Long tenantId, Long branchId, LayoutSaveRequest req) {
        TenantBranch branch = findOrThrow(tenantId, branchId);
        boolean takeout = req.takeoutOnly();
        int floors = Math.max(1, req.floorCount());
        branch.updateLayoutMeta(takeout, floors, req.canvasW(), req.canvasH());

        // code 기준 upsert — 이미 있는 테이블은 위치만 갱신(코드·id 유지 → QR 계속 유효),
        // 없어진 테이블은 삭제, 새 테이블은 삽입. 포장전문점이면 전부 삭제.
        List<BranchTable> existing = tableRepository.findByBranchIdOrderByFloorNoAscIdAsc(branchId);
        if (takeout || req.tables() == null || req.tables().isEmpty()) {
            tableRepository.deleteByBranchId(branchId);
        } else {
            Map<String, BranchTable> byCode = existing.stream()
                    .filter((e) -> e.getCode() != null)
                    .collect(Collectors.toMap(BranchTable::getCode, java.util.function.Function.identity(), (a, b) -> a));
            Set<String> keep = new HashSet<>();
            for (var t : req.tables()) {
                int floor = Math.min(Math.max(1, t.floorNo()), floors); // 층 범위 보정
                String code = (t.code() == null || t.code().isBlank())
                        ? java.util.UUID.randomUUID().toString() : t.code();
                keep.add(code);
                BranchTable ex = byCode.get(code);
                if (ex != null) {
                    ex.update(floor, t.label(), t.seats(), t.kind(), t.x(), t.y(), t.width(), t.height());
                } else {
                    tableRepository.save(BranchTable.of(branchId, code, floor, t.label(), t.seats(), t.kind(),
                            t.x(), t.y(), t.width(), t.height()));
                }
            }
            existing.stream().filter((e) -> !keep.contains(e.getCode())).forEach(tableRepository::delete);
        }
        return layout(tenantId, branchId);
    }

    /** QR 주문 토큰(code)으로 테이블을 찾는다. QR 이미지 생성·주문 페이지에서 쓴다. */
    @Transactional(readOnly = true)
    public BranchTable findByCode(Long tenantId, Long branchId, Long tableId) {
        findOrThrow(tenantId, branchId); // 업체·지점 매칭 확인
        BranchTable t = tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));
        if (!t.getBranchId().equals(branchId)) {
            throw new ApiException(ErrorCode.BRANCH_NOT_FOUND);
        }
        return t;
    }

    private void requireTenant(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException(ErrorCode.TENANT_NOT_FOUND);
        }
    }

    private TenantBranch findOrThrow(Long tenantId, Long branchId) {
        TenantBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));
        // 경로의 업체와 지점이 실제로 매칭되는지 확인(엉뚱한 업체의 지점을 건드리지 못하게).
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ApiException(ErrorCode.BRANCH_NOT_FOUND);
        }
        return branch;
    }
}
