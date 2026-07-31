package com.saas.admin.tenant.home;

import com.saas.admin.tenant.home.domain.TenantHome;
import jakarta.validation.constraints.Size;

/** 가게 메인 페이지 DTO. */
public final class TenantHomeDtos {

    private TenantHomeDtos() {
    }

    public record HomeView(String shopName, String heroImageUrl, String tagline, String intro,
                           String hours, String phone, String address, String notice,
                           String miniroom, boolean published) {
        public static HomeView of(String shopName, TenantHome h) {
            if (h == null) {
                return new HomeView(shopName, null, null, null, null, null, null, null, null, true);
            }
            return new HomeView(shopName, h.getHeroImageUrl(), h.getTagline(), h.getIntro(),
                    h.getHours(), h.getPhone(), h.getAddress(), h.getNotice(), h.getMiniroom(), h.isPublished());
        }
    }

    public record HomeSaveRequest(
            @Size(max = 500) String heroImageUrl,
            @Size(max = 120) String tagline,
            @Size(max = 1000) String intro,
            @Size(max = 200) String hours,
            @Size(max = 30) String phone,
            @Size(max = 255) String address,
            @Size(max = 500) String notice,
            @Size(max = 4000) String miniroom,
            boolean published
    ) {
    }
}
