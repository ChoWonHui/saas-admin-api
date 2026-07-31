package com.saas.admin.tenant.waitlist;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.order.domain.Order;
import com.saas.admin.order.domain.OrderStatus;
import com.saas.admin.order.repository.OrderRepository;
import com.saas.admin.tenant.TenantBranchService;
import com.saas.admin.tenant.dto.BranchDtos.LayoutResponse;
import com.saas.admin.tenant.dto.BranchDtos.TableDto;
import com.saas.admin.tenant.waitlist.WaitlistDtos.*;
import com.saas.admin.tenant.waitlist.domain.WaitlistEntry;
import com.saas.admin.tenant.waitlist.domain.WaitlistStatus;
import com.saas.admin.tenant.waitlist.domain.WaitlistType;
import com.saas.admin.tenant.waitlist.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 예약/대기 관리.
 * - 예약(RESERVATION): 미리 시간을 잡고 오는 손님. 테이블·예약일시 필수(연락처는 선택). 테이블을 골라 접수한다.
 * - 대기표(WAITING): 워킹인데 테이블이 꽉 찼을 때 순번 발급. 연락처 필수. 순번은 활성 대기 기준 채번.
 */
@Service
@RequiredArgsConstructor
public class TenantWaitlistService {

    // 테이블 점유로 보는 상태 = "진행 중" (종료·취소·결제완료가 아니면 자리 사용 중).
    private static final List<OrderStatus> FREE_STATUSES = List.of(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.PAID);
    private static final List<WaitlistStatus> ACTIVE =
            List.of(WaitlistStatus.RESERVED, WaitlistStatus.WAITING, WaitlistStatus.CALLED);

    private final WaitlistRepository waitlistRepository;
    private final OrderRepository orderRepository;
    private final TenantBranchService branchService;

    /** 예약/대기 화면 — 테이블 점유 현황 + 예약 목록 + 대기표 목록 + 배치도. */
    @Transactional(readOnly = true)
    public WaitlistBoard board(Long tenantId) {
        Long branchId = branchService.defaultBranchId(tenantId);
        LayoutResponse layout = branchService.layout(tenantId, branchId);
        List<TableDto> tds = layout.tables();
        Map<Long, TableDto> tableById = tds.stream()
                .collect(Collectors.toMap(TableDto::tableId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        int total = tableById.size();

        Set<Long> occupiedIds = orderRepository.findByTenantIdAndStatusNotInOrderByCreatedAtAsc(tenantId, FREE_STATUSES).stream()
                .map(Order::getTableId)
                .filter(id -> id != null && tableById.containsKey(id))
                .collect(Collectors.toSet());

        List<WaitlistEntry> active = waitlistRepository.findByTenantIdAndStatusInOrderByQueueNoAscIdAsc(tenantId, ACTIVE);

        // 테이블별 활성 예약 수(배치도에 배지로 표시).
        Map<Long, Long> rsvCountByTable = active.stream()
                .filter(e -> e.getType() == WaitlistType.RESERVATION && e.getTableId() != null)
                .collect(Collectors.groupingBy(WaitlistEntry::getTableId, Collectors.counting()));

        List<WaitlistEntryView> reservations = active.stream()
                .filter(e -> e.getType() == WaitlistType.RESERVATION)
                .sorted(Comparator.comparing(WaitlistEntry::getReservedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> toView(e, tableById))
                .toList();

        List<WaitlistEntryView> waiting = active.stream()
                .filter(e -> e.getType() == WaitlistType.WAITING)
                .map(e -> toView(e, tableById))
                .toList();

        List<TableSlot> slots = tds.stream()
                .map(t -> new TableSlot(t.tableId(), t.floorNo(), t.label(), t.seats(), t.kind(),
                        t.x(), t.y(), t.width(), t.height(),
                        occupiedIds.contains(t.tableId()),
                        rsvCountByTable.getOrDefault(t.tableId(), 0L).intValue()))
                .toList();

        boolean allFull = total > 0 && occupiedIds.size() >= total;
        return new WaitlistBoard(occupiedIds.size(), total, allFull,
                reservations.size(), waiting.size(), reservations, waiting,
                layout.floorCount(), layout.canvasW(), layout.canvasH(), slots);
    }

    private WaitlistEntryView toView(WaitlistEntry e, Map<Long, TableDto> tableById) {
        TableDto t = e.getTableId() == null ? null : tableById.get(e.getTableId());
        String label = t == null ? null : (t.label() == null || t.label().isBlank()
                ? ("ROOM".equals(t.kind()) ? "룸" : "테이블") : t.label());
        Integer floorNo = t == null ? null : t.floorNo();
        return WaitlistEntryView.of(e, label, floorNo);
    }

    /** 예약 접수 / 대기표 발급. 유형별 필수값을 검증하고, 대기표에만 순번을 부여한다. */
    @Transactional
    public WaitlistEntryView add(Long tenantId, WaitlistAddRequest req) {
        Long branchId = branchService.defaultBranchId(tenantId);
        WaitlistType type = req.type();

        // 대기표(WAITING)는 손님에게 연락해야 하므로 연락처 필수. 예약은 테이블·시간으로 관리하므로 연락처는 선택.
        if (type == WaitlistType.WAITING && (req.phone() == null || req.phone().isBlank())) {
            throw new ApiException(ErrorCode.WAITLIST_PHONE_REQUIRED);
        }
        LocalDateTime reservedAt = null;
        Long tableId = null;
        int queueNo = 0;
        if (type == WaitlistType.RESERVATION) {
            if (req.tableId() == null) {
                throw new ApiException(ErrorCode.WAITLIST_TABLE_REQUIRED);
            }
            if (req.reservedAt() == null) {
                throw new ApiException(ErrorCode.WAITLIST_RESERVED_AT_REQUIRED);
            }
            // 선택한 테이블이 이 업체(지점) 것인지 확인.
            boolean owns = branchService.layout(tenantId, branchId).tables().stream()
                    .anyMatch(t -> t.tableId().equals(req.tableId()));
            if (!owns) {
                throw new ApiException(ErrorCode.WAITLIST_TABLE_NOT_FOUND);
            }
            reservedAt = req.reservedAt();
            tableId = req.tableId();
        } else {
            queueNo = nextQueueNo(tenantId);
        }

        WaitlistEntry saved = waitlistRepository.save(WaitlistEntry.create(
                tenantId, branchId, type, queueNo, reservedAt, tableId,
                req.partyName(), req.partySize(), req.phone(), req.memo()));
        return viewOf(tenantId, saved);
    }

    private WaitlistEntryView viewOf(Long tenantId, WaitlistEntry saved) {
        Long branchId = branchService.defaultBranchId(tenantId);
        Map<Long, TableDto> tableById = branchService.layout(tenantId, branchId).tables().stream()
                .collect(Collectors.toMap(TableDto::tableId, Function.identity(), (a, b) -> a));
        return toView(saved, tableById);
    }

    /** 대기표 순번 = 활성 대기표 중 최대 순번 + 1(없으면 1번). */
    private int nextQueueNo(Long tenantId) {
        return waitlistRepository.findByTenantIdAndStatusInOrderByQueueNoAscIdAsc(tenantId, ACTIVE).stream()
                .filter(e -> e.getType() == WaitlistType.WAITING)
                .mapToInt(WaitlistEntry::getQueueNo).max().orElse(0) + 1;
    }

    /** 상태 변경 — 호출(CALLED) / 착석(SEATED) 만. */
    @Transactional
    public WaitlistEntryView changeStatus(Long tenantId, Long id, WaitlistStatus status) {
        if (status != WaitlistStatus.CALLED && status != WaitlistStatus.SEATED) {
            throw new ApiException(ErrorCode.WAITLIST_INVALID_STATUS);
        }
        WaitlistEntry entry = require(tenantId, id);
        entry.changeStatus(status);
        return viewOf(tenantId, entry);
    }

    /** 취소 — 소프트 삭제(상태 CANCELLED). */
    @Transactional
    public void cancel(Long tenantId, Long id) {
        require(tenantId, id).changeStatus(WaitlistStatus.CANCELLED);
    }

    private WaitlistEntry require(Long tenantId, Long id) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.WAITLIST_NOT_FOUND));
        if (!entry.belongsTo(tenantId)) {
            throw new ApiException(ErrorCode.WAITLIST_NOT_FOUND);
        }
        return entry;
    }
}
