package com.saas.admin.tenant.waitlist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 예약/대기 접수 한 건. 미리 시간을 잡는 예약(RESERVATION)과, 테이블이 꽉 찼을 때 현장 워킹 손님에게
 * 순번을 주는 대기표(WAITING)를 함께 다룬다.
 * 플랫폼 공통 데이터가 아니라 업체(tenant) 데이터다. 테이블은 Hibernate 가 만든다(새 테이블 — enum 은 VARCHAR).
 */
@Entity
@Table(name = "waitlist_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waitlist_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** 접수 유형 — 예약(RESERVATION) / 대기표(WAITING). */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private WaitlistType type;

    /** 대기 순번(대기표에만 부여. 업체 안 활성 대기 기준 채번. 예약은 0). */
    @Column(name = "queue_no", nullable = false)
    private int queueNo;

    /** 예약 일시(예약에만 사용). */
    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    /** 예약 대상 테이블(예약에만 사용. 대기표는 null). */
    @Column(name = "table_id")
    private Long tableId;

    /** 대기자 이름(선택). */
    @Column(name = "party_name", length = 40)
    private String partyName;

    /** 인원수. */
    @Column(name = "party_size", nullable = false)
    private int partySize;

    /** 연락처(선택). */
    @Column(name = "phone", length = 30)
    private String phone;

    /** 메모(선택). */
    @Column(name = "memo", length = 200)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static WaitlistEntry create(Long tenantId, Long branchId, WaitlistType type, int queueNo,
                                       LocalDateTime reservedAt, Long tableId, String partyName, int partySize,
                                       String phone, String memo) {
        WaitlistEntry w = new WaitlistEntry();
        w.tenantId = tenantId;
        w.branchId = branchId;
        w.type = type;
        w.queueNo = queueNo;
        w.reservedAt = reservedAt;
        w.tableId = tableId;
        w.partyName = blankToNull(partyName);
        w.partySize = partySize;
        w.phone = blankToNull(phone);
        w.memo = blankToNull(memo);
        w.status = (type == WaitlistType.RESERVATION) ? WaitlistStatus.RESERVED : WaitlistStatus.WAITING;
        return w;
    }

    public void changeStatus(WaitlistStatus next) {
        this.status = next;
    }

    public boolean belongsTo(Long tenantId) {
        return this.tenantId != null && this.tenantId.equals(tenantId);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
