package com.saas.admin.tenant.home;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.home.TenantHomeDtos.HomeSaveRequest;
import com.saas.admin.tenant.home.TenantHomeDtos.HomeView;
import com.saas.admin.tenant.home.domain.TenantHome;
import com.saas.admin.tenant.home.repository.TenantHomeRepository;
import com.saas.admin.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가게 메인 페이지 콘텐츠 — 사장님이 편집(get/save), 손님이 조회(publicView). */
@Service
@RequiredArgsConstructor
public class TenantHomeService {

    private final TenantHomeRepository homeRepository;
    private final TenantRepository tenantRepository;

    /** 사장님 콘솔용 — 현재 홈 콘텐츠(없으면 가게명만 채운 빈 값). */
    @Transactional(readOnly = true)
    public HomeView get(Long tenantId) {
        Tenant tenant = requireTenant(tenantId);
        return HomeView.of(tenant.getName(), homeRepository.findByTenantId(tenantId).orElse(null));
    }

    /** 저장(upsert). */
    @Transactional
    public HomeView save(Long tenantId, HomeSaveRequest req) {
        Tenant tenant = requireTenant(tenantId);
        TenantHome home = homeRepository.findByTenantId(tenantId)
                .orElseGet(() -> homeRepository.save(TenantHome.create(tenantId)));
        home.update(req.heroImageUrl(), req.tagline(), req.intro(), req.hours(),
                req.phone(), req.address(), req.notice(), req.miniroom(), req.published());
        return HomeView.of(tenant.getName(), home);
    }

    /** 손님용 — 표시(published) 상태일 때만 콘텐츠, 아니면 published=false 로 가게명만. */
    @Transactional(readOnly = true)
    public HomeView publicView(Long tenantId, String shopName) {
        TenantHome home = homeRepository.findByTenantId(tenantId).orElse(null);
        if (home == null || !home.isPublished()) {
            return new HomeView(shopName, null, null, null, null, null, null, null, null, false);
        }
        return HomeView.of(shopName, home);
    }

    private Tenant requireTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
    }
}
