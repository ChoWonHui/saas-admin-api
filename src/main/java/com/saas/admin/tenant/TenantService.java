package com.saas.admin.tenant;

import com.saas.admin.auth.domain.TenantUser;
import com.saas.admin.auth.domain.UserAccount;
import com.saas.admin.auth.repository.TenantUserRepository;
import com.saas.admin.auth.repository.UserAccountRepository;
import com.saas.admin.common.audit.AuditLog;
import com.saas.admin.common.audit.AuditResult;
import com.saas.admin.common.audit.AuditService;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.domain.*;
import com.saas.admin.tenant.dto.CreateTenantRequest;
import com.saas.admin.tenant.dto.CreateTenantResponse;
import com.saas.admin.tenant.dto.TenantResponse;
import com.saas.admin.tenant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ReservedSlugRepository reservedSlugRepository;
    private final TenantPlanRepository tenantPlanRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * 업체 등록 — 업체 + 대표 계정 + 구독을 한 트랜잭션에서 만든다.
     * 업체는 PENDING 으로 시작한다. 플랫폼 관리자가 별도로 개설(ACTIVE)해야 고객 화면에 노출된다.
     */
    @Transactional
    public CreateTenantResponse register(CreateTenantRequest request, Long actorId, String ip, String userAgent) {
        String slug = normalizeSlug(request.tenantSlug());

        validateSlug(slug);

        if (userAccountRepository.existsByEmail(request.ownerEmail())) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }

        TenantPlan plan = tenantPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND));

        Tenant tenant = tenantRepository.save(Tenant.register(
                nextTenantCode(),
                request.tenantName(),
                slug,
                plan.getId(),
                request.ownerName(),
                request.businessNo(),
                request.contactPhone(),
                request.contactEmail(),
                actorId));

        UserAccount owner = userAccountRepository.save(UserAccount.createTenantUser(
                request.ownerEmail(),
                passwordEncoder.encode(request.ownerPassword()),
                request.ownerAccountName(),
                request.contactPhone()));

        // role_id = 2 (TENANT_OWNER). DB 의 owner_marker 가 테넌트당 대표 1명을 강제한다.
        tenantUserRepository.save(TenantUser.createOwner(tenant.getId(), owner.getId()));

        subscriptionRepository.save(TenantSubscription.start(tenant.getId(), plan.getId()));

        auditService.record(AuditLog.builder()
                .tenantId(tenant.getId())
                .actorUserId(actorId)
                .actorRole("PLATFORM_ADMIN")
                .action("TENANT_CREATE")
                .resourceType("TENANT")
                .resourceId(String.valueOf(tenant.getId()))
                .result(AuditResult.SUCCESS)
                .message("slug=" + slug + ", plan=" + plan.getCode())
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());

        return new CreateTenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getSlug(),
                tenant.getStatus().name(),
                owner.getId(),
                owner.getEmail());
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> list(TenantStatus status, Pageable pageable) {
        Page<Tenant> page = (status == null)
                ? tenantRepository.findAll(pageable)
                : tenantRepository.findByStatus(status, pageable);
        return page.map(TenantResponse::from);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(TenantResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
    }

    /** 서비스 개설 */
    @Transactional
    public TenantResponse activate(Long tenantId, Long actorId, String ip, String userAgent) {
        Tenant tenant = findOrThrow(tenantId);
        tenant.activate(actorId);
        audit(tenant, actorId, "TENANT_ACTIVATE", null, ip, userAgent);
        return TenantResponse.from(tenant);
    }

    /** 서비스 중지 — 고객 화면은 404 가 아니라 503 을 낸다. (설계안 §4.1) */
    @Transactional
    public TenantResponse suspend(Long tenantId, String reason, Long actorId, String ip, String userAgent) {
        Tenant tenant = findOrThrow(tenantId);
        tenant.suspend(reason, actorId);
        audit(tenant, actorId, "TENANT_SUSPEND", reason, ip, userAgent);
        return TenantResponse.from(tenant);
    }

    private Tenant findOrThrow(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
    }

    private void audit(Tenant tenant, Long actorId, String action, String message, String ip, String userAgent) {
        auditService.record(AuditLog.builder()
                .tenantId(tenant.getId())
                .actorUserId(actorId)
                .actorRole("PLATFORM_ADMIN")
                .action(action)
                .resourceType("TENANT")
                .resourceId(String.valueOf(tenant.getId()))
                .result(AuditResult.SUCCESS)
                .message(message)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }

    /**
     * slug 검증 (설계안 §2.1, §2.2).
     * 라우팅이 우연히 동작하는 데 기대지 않고 등록 시점에 막는다.
     */
    private void validateSlug(String slug) {
        if (!SlugPolicy.isValid(slug)) {
            throw new ApiException(ErrorCode.SLUG_INVALID_FORMAT,
                    "경로는 영소문자·숫자·하이픈만 쓸 수 있고 3~30자여야 하며, "
                            + "하이픈으로 시작·종료하거나 연속될 수 없습니다.");
        }
        if (reservedSlugRepository.existsBySlug(slug)) {
            throw new ApiException(ErrorCode.SLUG_RESERVED);
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new ApiException(ErrorCode.SLUG_DUPLICATED);
        }
    }

    private String normalizeSlug(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * SHOP0001 형식의 업체 코드를 만든다.
     * 동시 등록으로 같은 코드가 나오면 uk_tenant__code 가 막는다 — 그 경우 명시적 에러로 바꿔
     * 클라이언트가 재시도하게 한다. (조용히 실패하지 않는다)
     */
    private String nextTenantCode() {
        try {
            long seq = tenantRepository.nextTenantCodeSequence();
            return String.format("SHOP%04d", seq);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.TENANT_CODE_GENERATION_FAILED);
        }
    }
}
