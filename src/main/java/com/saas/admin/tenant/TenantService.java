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
import com.saas.admin.tenant.dto.TenantCreateRequest;
import com.saas.admin.tenant.dto.TenantPlanResponse;
import com.saas.admin.tenant.dto.TenantResponse;
import com.saas.admin.tenant.dto.TenantUpdateRequest;
import com.saas.admin.tenant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantBranchRepository branchRepository;
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

        // 대표 로그인 아이디 — 이메일 앞부분에서 허용 문자만 뽑아 만든다. 새 업체의 첫 계정이라 충돌이 없다.
        String ownerLoginId = request.ownerEmail().split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (ownerLoginId.length() < 3) ownerLoginId = "owner";

        UserAccount owner = userAccountRepository.save(UserAccount.createTenantUser(
                request.ownerEmail(),
                ownerLoginId,
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

    /**
     * 업체 등록(업체 정보만). 대표 계정·구독은 만들지 않는다.
     * PENDING 으로 시작하고, 고객 화면 노출은 /activate 로 별도 개설한다.
     */
    @Transactional
    public TenantResponse create(TenantCreateRequest request, Long actorId, String ip, String userAgent) {
        String slug = normalizeSlug(request.tenantSlug());
        validateSlug(slug);

        Long planId = null;
        if (request.planId() != null) {
            tenantPlanRepository.findById(request.planId())
                    .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND));
            planId = request.planId();
        }

        Tenant tenant = tenantRepository.save(Tenant.create(
                nextTenantCode(),
                request.tenantName(),
                slug,
                planId,
                request.ownerName(),
                request.businessNo(),
                request.contactPhone(),
                request.contactEmail(),
                request.postalCode(),
                request.address(),
                request.addressDetail(),
                actorId));

        audit(tenant, actorId, "TENANT_CREATE", "slug=" + slug, ip, userAgent);
        return TenantResponse.from(tenant);
    }

    /** 업체 정보 수정. slug 는 바꿀 수 있고(검증), code·status 는 바꾸지 않는다. */
    @Transactional
    public TenantResponse update(Long tenantId, TenantUpdateRequest request, Long actorId, String ip, String userAgent) {
        Tenant tenant = findOrThrow(tenantId);

        // slug 변경 요청이 있고 실제로 달라졌을 때만 검증 후 변경한다.
        if (request.tenantSlug() != null && !request.tenantSlug().isBlank()) {
            String newSlug = normalizeSlug(request.tenantSlug());
            if (!newSlug.equals(tenant.getSlug())) {
                validateSlug(newSlug); // 형식 + 예약어 + 중복(다른 업체)
                tenant.changeSlug(newSlug);
            }
        }

        if (request.planId() != null) {
            tenantPlanRepository.findById(request.planId())
                    .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND));
        }
        tenant.update(request.tenantName(), request.planId(), request.ownerName(), request.businessNo(),
                request.contactPhone(), request.contactEmail(), request.postalCode(),
                request.address(), request.addressDetail(), actorId);
        audit(tenant, actorId, "TENANT_UPDATE", null, ip, userAgent);
        return TenantResponse.from(tenant);
    }

    /** 소프트 삭제(삭제여부='Y'). 상태(CLOSED)와 별개. */
    @Transactional
    public void delete(Long tenantId, Long actorId, String ip, String userAgent) {
        Tenant tenant = findOrThrow(tenantId);
        tenant.markDeleted(actorId);
        audit(tenant, actorId, "TENANT_DELETE", null, ip, userAgent);
    }

    /** 삭제 복구. */
    @Transactional
    public TenantResponse restore(Long tenantId, Long actorId, String ip, String userAgent) {
        Tenant tenant = findOrThrow(tenantId);
        tenant.restore(actorId);
        audit(tenant, actorId, "TENANT_RESTORE", null, ip, userAgent);
        return TenantResponse.from(tenant);
    }

    /** 요금제 목록(콤보박스용). */
    @Transactional(readOnly = true)
    public List<TenantPlanResponse> plans() {
        return tenantPlanRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(TenantPlanResponse::from)
                .toList();
    }

    /** 목록. 기본은 삭제된 업체 제외(includeDeleted=true 면 포함). status 로 추가 필터. 지점 수를 함께 준다. */
    @Transactional(readOnly = true)
    public Page<TenantResponse> list(TenantStatus status, boolean includeDeleted, Pageable pageable) {
        Page<Tenant> page;
        if (includeDeleted) {
            page = (status == null)
                    ? tenantRepository.findAll(pageable)
                    : tenantRepository.findByStatus(status, pageable);
        } else {
            page = (status == null)
                    ? tenantRepository.findByDeleted("N", pageable)
                    : tenantRepository.findByStatusAndDeleted(status, "N", pageable);
        }
        Map<Long, Long> branchCounts = branchCountMap(page.getContent().stream().map(Tenant::getId).toList());
        return page.map((t) -> TenantResponse.from(t, branchCounts.getOrDefault(t.getId(), 0L)));
    }

    /** 여러 업체의 지점 수를 한 번에 모아 맵으로. */
    private Map<Long, Long> branchCountMap(List<Long> tenantIds) {
        if (tenantIds.isEmpty()) return Map.of();
        return branchRepository.countByTenantIdIn(tenantIds).stream()
                .collect(java.util.stream.Collectors.toMap((r) -> (Long) r[0], (r) -> (Long) r[1]));
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
     * 업체 코드를 만든다. 예측을 막기 위해 <b>고정 접두어 없이 전부 랜덤</b>이다.
     * 형식: 10자리(헷갈리는 0/O/1/I 제외한 대문자·숫자). 충돌하면 다시 뽑는다.
     */
    private String nextTenantCode() {
        for (int i = 0; i < 20; i++) {
            String code = randomCode(10);
            if (!tenantRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ApiException(ErrorCode.TENANT_CODE_GENERATION_FAILED);
    }

    /** 혼동되는 문자(0/O/1/I)를 뺀 영대문자·숫자 랜덤 문자열. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final java.security.SecureRandom codeRandom = new java.security.SecureRandom();

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_ALPHABET.charAt(codeRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
