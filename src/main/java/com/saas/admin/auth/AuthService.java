package com.saas.admin.auth;

import com.saas.admin.auth.domain.*;
import com.saas.admin.auth.dto.*;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.auth.jwt.JwtTokenProvider;
import com.saas.admin.auth.repository.*;
import com.saas.admin.common.audit.AuditLog;
import com.saas.admin.common.audit.AuditResult;
import com.saas.admin.common.audit.AuditService;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.domain.TenantStatus;
import com.saas.admin.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final UserAccountRepository userAccountRepository;
    private final TenantUserRepository tenantUserRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;

    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        UserAccount user = userAccountRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    // 존재하지 않는 이메일과 틀린 비밀번호를 구분해서 알려주지 않는다 (계정 존재 여부 유출 방지).
                    auditLoginFail(null, request.email(), "존재하지 않는 이메일", ip, userAgent);
                    return new ApiException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!user.isUsable()) {
            auditLoginFail(user.getId(), request.email(), "사용 불가 계정: " + user.getStatus(), ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.isLocked()) {
            auditLoginFail(user.getId(), request.email(), "잠긴 계정", ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.onLoginFail();
            userAccountRepository.save(user);
            auditLoginFail(user.getId(), request.email(), "비밀번호 불일치", ip, userAgent);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.onLoginSuccess();
        userAccountRepository.save(user);

        // 플랫폼 관리자는 테넌트 컨텍스트가 없다. 업체 사용자는 소속 목록을 받아 하나를 골라야 한다.
        List<MembershipResponse> memberships = user.isPlatformAdmin()
                ? List.of()
                : loadMemberships(user.getId());

        String roleCode = user.isPlatformAdmin() ? PLATFORM_ADMIN_ROLE : null;
        String accessToken = tokenProvider.createAccessToken(
                user.getId(), user.getEmail(), null, roleCode, user.isPlatformAdmin());
        String refreshToken = issueRefreshToken(user.getId(), null, ip, userAgent);

        auditService.record(AuditLog.builder()
                .actorUserId(user.getId())
                .actorRole(roleCode)
                .action("LOGIN_SUCCESS")
                .resourceType("USER_ACCOUNT")
                .resourceId(String.valueOf(user.getId()))
                .result(AuditResult.SUCCESS)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());

        return new LoginResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenValiditySeconds(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.isPlatformAdmin(),
                memberships);
    }

    /**
     * 업체 선택 → 테넌트 컨텍스트가 담긴 토큰 재발급. (설계안 §11)
     * 프론트가 보낸 tenantId 는 신뢰하지 않고, tenant_user 에서 실제 소속을 재검증한다.
     */
    @Transactional
    public TokenResponse selectTenant(AuthPrincipal principal, Long tenantId, String ip, String userAgent) {
        TenantUser membership = tenantUserRepository
                .findByTenantIdAndUserId(tenantId, principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_A_MEMBER));

        if (!membership.isActive()) {
            throw new ApiException(ErrorCode.NOT_A_MEMBER, "해당 업체에서 활성 상태가 아닙니다.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));

        if (tenant.getStatus() == TenantStatus.CLOSED) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "해지된 업체입니다.");
        }

        String roleCode = roleRepository.findById(membership.getRoleId())
                .map(Role::getCode)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));

        UserAccount user = userAccountRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

        String accessToken = tokenProvider.createAccessToken(
                user.getId(), user.getEmail(), tenantId, roleCode, user.isPlatformAdmin());
        String refreshToken = issueRefreshToken(user.getId(), tenantId, ip, userAgent);

        return new TokenResponse(accessToken, refreshToken,
                tokenProvider.getAccessTokenValiditySeconds(), tenantId, roleCode);
    }

    /**
     * 업체 로그인 — 업체코드 + 아이디 + 비밀번호로 한 번에 로그인한다.
     * 업체코드가 이미 가게를 특정하므로, 이메일 로그인과 달리 업체 선택 단계 없이
     * 곧바로 테넌트 컨텍스트가 담긴 토큰을 발급한다.
     */
    @Transactional
    public TokenResponse tenantLogin(String tenantCode, String loginId, String password,
                                     String ip, String userAgent) {
        String label = tenantCode + "/" + loginId; // 감사 로그용 식별자
        // 업체 로그인은 이메일이 아니라 업체코드/아이디를 쓰므로 안내 문구를 맞춘다.
        String badMsg = "업체코드·아이디·비밀번호를 확인해 주세요.";

        // 업체코드가 틀리거나 아이디가 없거나 비밀번호가 틀린 것을 구분해 알려주지 않는다(계정 존재 유출 방지).
        Tenant tenant = tenantRepository.findByCode(tenantCode.trim())
                .filter(t -> t.getStatus() != TenantStatus.CLOSED && !t.isDeleted())
                .orElseThrow(() -> {
                    auditLoginFail(null, label, "업체코드 불일치", ip, userAgent);
                    return new ApiException(ErrorCode.INVALID_CREDENTIALS, badMsg);
                });

        // 아이디로 먼저 찾고, 없으면 이메일로 보조 조회한다(로그인 칸에 이메일을 넣는 사람 배려).
        String idInput = loginId.trim();
        UserAccount user = userAccountRepository.findByTenantIdAndLoginId(tenant.getId(), idInput)
                .or(() -> userAccountRepository.findByTenantIdAndEmail(tenant.getId(), idInput))
                .orElseThrow(() -> {
                    auditLoginFail(null, label, "아이디/이메일 없음", ip, userAgent);
                    return new ApiException(ErrorCode.INVALID_CREDENTIALS, badMsg);
                });

        if (!user.isUsable()) {
            auditLoginFail(user.getId(), label, "사용 불가 계정: " + user.getStatus(), ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.isLocked()) {
            auditLoginFail(user.getId(), label, "잠긴 계정", ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.onLoginFail();
            userAccountRepository.save(user);
            auditLoginFail(user.getId(), label, "비밀번호 불일치", ip, userAgent);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, badMsg);
        }

        user.onLoginSuccess();
        userAccountRepository.save(user);

        TenantUser membership = tenantUserRepository
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_A_MEMBER));
        if (!membership.isActive()) {
            throw new ApiException(ErrorCode.NOT_A_MEMBER, "해당 가게에서 활성 상태가 아닙니다.");
        }

        String roleCode = roleRepository.findById(membership.getRoleId())
                .map(Role::getCode)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));

        String accessToken = tokenProvider.createAccessToken(
                user.getId(), user.getEmail(), tenant.getId(), roleCode, false);
        String refreshToken = issueRefreshToken(user.getId(), tenant.getId(), ip, userAgent);

        auditService.record(AuditLog.builder()
                .actorUserId(user.getId())
                .actorRole(roleCode)
                .action("LOGIN_SUCCESS")
                .resourceType("USER_ACCOUNT")
                .resourceId(String.valueOf(user.getId()))
                .result(AuditResult.SUCCESS)
                .message("업체 로그인: " + label)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());

        return new TokenResponse(accessToken, refreshToken,
                tokenProvider.getAccessTokenValiditySeconds(), tenant.getId(), roleCode);
    }

    /** 로그인 전, 업체코드로 가게 이름만 확인한다(자동입력 링크에서 "우리 가게 맞음" 표시용). */
    @Transactional(readOnly = true)
    public TenantLookupResponse tenantLookup(String code) {
        if (code == null || code.isBlank()) {
            return TenantLookupResponse.notFound();
        }
        return tenantRepository.findByCode(code.trim())
                .filter(t -> t.getStatus() != TenantStatus.CLOSED && !t.isDeleted())
                .map(t -> TenantLookupResponse.of(t.getName()))
                .orElseGet(TenantLookupResponse::notFound);
    }

    /** 리프레시 토큰 회전 — 쓴 토큰은 즉시 폐기하고 새 토큰을 발급한다. */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String ip, String userAgent) {
        String hash = tokenProvider.hashRefreshToken(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

        if (!stored.isUsable()) {
            throw new ApiException(ErrorCode.EXPIRED_TOKEN);
        }

        UserAccount user = userAccountRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

        if (!user.isUsable()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 토큰 발급 이후 권한이 회수됐을 수 있다. 테넌트 컨텍스트가 있으면 소속을 다시 확인한다.
        Long tenantId = stored.getTenantId();
        String roleCode = user.isPlatformAdmin() ? PLATFORM_ADMIN_ROLE : null;

        if (tenantId != null) {
            TenantUser membership = tenantUserRepository
                    .findByTenantIdAndUserId(tenantId, user.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_A_MEMBER));
            if (!membership.isActive()) {
                throw new ApiException(ErrorCode.NOT_A_MEMBER);
            }
            roleCode = roleRepository.findById(membership.getRoleId())
                    .map(Role::getCode)
                    .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
        }

        stored.revoke();
        refreshTokenRepository.save(stored);

        String accessToken = tokenProvider.createAccessToken(
                user.getId(), user.getEmail(), tenantId, roleCode, user.isPlatformAdmin());
        String newRefreshToken = issueRefreshToken(user.getId(), tenantId, ip, userAgent);

        return new TokenResponse(accessToken, newRefreshToken,
                tokenProvider.getAccessTokenValiditySeconds(), tenantId, roleCode);
    }

    @Transactional
    public void logout(AuthPrincipal principal) {
        refreshTokenRepository.revokeAllByUserId(principal.userId(), java.time.LocalDateTime.now());
    }

    private List<MembershipResponse> loadMemberships(Long userId) {
        List<TenantUser> memberships = tenantUserRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        // N+1 을 피하려고 테넌트와 역할을 한 번에 끌어와 맵으로 만든다.
        Map<Long, Tenant> tenants = tenantRepository
                .findAllById(memberships.stream().map(TenantUser::getTenantId).toList())
                .stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        Map<Integer, Role> roles = roleRepository
                .findAllById(memberships.stream().map(TenantUser::getRoleId).toList())
                .stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));

        return memberships.stream()
                .filter(m -> tenants.containsKey(m.getTenantId()))
                .map(m -> {
                    Tenant tenant = tenants.get(m.getTenantId());
                    Role role = roles.get(m.getRoleId());
                    return new MembershipResponse(
                            tenant.getId(),
                            tenant.getName(),
                            tenant.getSlug(),
                            tenant.getStatus().name(),
                            role == null ? null : role.getCode());
                })
                .toList();
    }

    private String issueRefreshToken(Long userId, Long tenantId, String ip, String userAgent) {
        String raw = tokenProvider.createRefreshToken();
        refreshTokenRepository.save(RefreshToken.issue(
                userId,
                tenantId,
                tokenProvider.hashRefreshToken(raw),
                tokenProvider.refreshTokenExpiry(),
                userAgent,
                ip));
        return raw;
    }

    private void auditLoginFail(Long userId, String email, String reason, String ip, String userAgent) {
        auditService.record(AuditLog.builder()
                .actorUserId(userId)
                .action("LOGIN_FAIL")
                .resourceType("USER_ACCOUNT")
                .resourceId(email)
                .result(AuditResult.FAILURE)
                .message(reason)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }
}
