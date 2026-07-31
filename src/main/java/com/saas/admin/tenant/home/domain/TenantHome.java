package com.saas.admin.tenant.home.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 가게 메인(공개) 페이지 콘텐츠. 사장님이 콘솔 홈에서 편집하고, 손님 QR 화면 상단에 보인다.
 * 업체당 1개(tenant_id 유니크).
 */
@Entity
@Table(name = "tenant_home", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_home", columnNames = "tenant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantHome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "home_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 대표 이미지(히어로). */
    @Column(name = "hero_image_url", length = 500)
    private String heroImageUrl;

    /** 한줄 소개(가게명 아래 문구). */
    @Column(name = "tagline", length = 120)
    private String tagline;

    /** 가게 소개(여러 줄). */
    @Column(name = "intro", length = 1000)
    private String intro;

    /** 영업시간(자유 텍스트). */
    @Column(name = "hours", length = 200)
    private String hours;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    /** 안내/공지 한마디. */
    @Column(name = "notice", length = 500)
    private String notice;

    /** 미니룸(가게 정체성) — 테마+배치 스티커 JSON. 사장님이 꾸미고 손님 랜딩에 보인다. */
    @Column(name = "miniroom", length = 4000)
    private String miniroom;

    /** 손님 화면에 표시할지. 'N' 이면 손님에게 안 보인다. */
    @Column(name = "published", nullable = false, length = 1, columnDefinition = "CHAR(1) NOT NULL DEFAULT 'Y'")
    private String published;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static TenantHome create(Long tenantId) {
        TenantHome h = new TenantHome();
        h.tenantId = tenantId;
        h.published = "Y";
        return h;
    }

    public void update(String heroImageUrl, String tagline, String intro, String hours,
                       String phone, String address, String notice, String miniroom, boolean published) {
        this.heroImageUrl = blank(heroImageUrl);
        this.tagline = blank(tagline);
        this.intro = blank(intro);
        this.hours = blank(hours);
        this.phone = blank(phone);
        this.address = blank(address);
        this.notice = blank(notice);
        this.miniroom = blank(miniroom);
        this.published = published ? "Y" : "N";
    }

    public boolean isPublished() {
        return "Y".equals(published);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
