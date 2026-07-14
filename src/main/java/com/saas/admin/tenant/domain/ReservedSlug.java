package com.saas.admin.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약어 slug 블랙리스트. 업체가 /admin, /api 같은 경로를 선점하면 플랫폼 라우팅이 깨진다.
 * 라우팅이 우연히 동작하는 데 기대지 않고 <b>등록 시점에 차단</b>한다. (설계안 §2.2)
 */
@Entity
@Table(name = "reserved_slug")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservedSlug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reserved_slug_id")
    private Integer id;

    @Column(name = "slug", nullable = false, length = 50)
    private String slug;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
