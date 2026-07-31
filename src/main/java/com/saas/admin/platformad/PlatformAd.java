package com.saas.admin.platformad;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 플랫폼(본사) 광고 배너 — 전 매장 손님 화면 하단에 공통으로 노출된다. 단일 행(본사가 관리).
 * 사장님은 손댈 수 없다.
 */
@Entity
@Table(name = "platform_ad")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformAd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ad_id")
    private Long id;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "link", length = 500)
    private String link;

    @Column(name = "ad_text", length = 200)
    private String text;

    /** 노출 여부. 'N' 이면 손님 화면에 안 보인다. */
    @Column(name = "enabled", nullable = false, length = 1, columnDefinition = "CHAR(1) NOT NULL DEFAULT 'Y'")
    private String enabled;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static PlatformAd blank() {
        PlatformAd a = new PlatformAd();
        a.enabled = "Y";
        return a;
    }

    public void update(String imageUrl, String link, String text, boolean enabled) {
        this.imageUrl = blank(imageUrl);
        this.link = blank(link);
        this.text = blank(text);
        this.enabled = enabled ? "Y" : "N";
    }

    public boolean isEnabled() {
        return "Y".equals(enabled);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
