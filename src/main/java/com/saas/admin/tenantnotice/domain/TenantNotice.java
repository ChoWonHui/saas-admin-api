package com.saas.admin.tenantnotice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 업체 공지사항 — 플랫폼 관리자가 등록하고 모든 업체(사장님)가 조회한다.
 * 본문은 리치 에디터 HTML(LONGTEXT). 상단 고정 + 팝업(기간) 지원.
 * (사내 공지 {@code notice} 와 별개 테이블이다: 대상과 노출 채널이 다르다)
 */
@Entity
@Table(name = "tenant_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_notice_id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 작성한 플랫폼 관리자 사번. 수정·삭제 권한의 기준. */
    @Column(name = "author_emp_no", nullable = false, length = 6)
    private String authorEmpNo;

    @Column(name = "pinned", nullable = false, length = 1)
    private String pinned;

    /** 팝업으로 띄울지. 'Y'/'N'. */
    @Column(name = "popup_enabled", nullable = false, length = 1)
    private String popupEnabled;

    /** 팝업 노출 시작/종료(둘 다 null 이면 팝업 켜져 있는 동안 항상 노출). */
    @Column(name = "popup_start_at")
    private LocalDateTime popupStartAt;

    @Column(name = "popup_end_at")
    private LocalDateTime popupEndAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TenantNotice create(String title, String content, String authorEmpNo, boolean pinned,
                                      boolean popupEnabled, LocalDateTime popupStartAt, LocalDateTime popupEndAt) {
        TenantNotice n = new TenantNotice();
        n.title = title;
        n.content = content;
        n.authorEmpNo = authorEmpNo;
        n.pinned = pinned ? "Y" : "N";
        n.popupEnabled = popupEnabled ? "Y" : "N";
        n.popupStartAt = popupStartAt;
        n.popupEndAt = popupEndAt;
        n.viewCount = 0;
        return n;
    }

    public void update(String title, String content, boolean pinned,
                       boolean popupEnabled, LocalDateTime popupStartAt, LocalDateTime popupEndAt) {
        this.title = title;
        this.content = content;
        this.pinned = pinned ? "Y" : "N";
        this.popupEnabled = popupEnabled ? "Y" : "N";
        this.popupStartAt = popupStartAt;
        this.popupEndAt = popupEndAt;
    }

    public void increaseView() {
        this.viewCount++;
    }

    public boolean isPinned() {
        return "Y".equals(pinned);
    }

    public boolean isPopupEnabled() {
        return "Y".equals(popupEnabled);
    }

    /** 지금 팝업으로 띄워야 하는가(켜져 있고 기간 안). */
    public boolean isPopupActive(LocalDateTime now) {
        if (!isPopupEnabled()) return false;
        if (popupStartAt != null && now.isBefore(popupStartAt)) return false;
        if (popupEndAt != null && now.isAfter(popupEndAt)) return false;
        return true;
    }

    public boolean isAuthor(String empNo) {
        return authorEmpNo.equals(empNo);
    }
}
