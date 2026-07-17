package com.saas.admin.adminaccount;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.domain.AdminRefreshToken;
import com.saas.admin.adminaccount.dto.AdminDtos.AdminTokenResponse;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.adminaccount.repository.AdminRefreshTokenRepository;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.audit.AuditLog;
import com.saas.admin.common.audit.AuditResult;
import com.saas.admin.common.audit.AuditService;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 인증. 업체 사용자와 <b>완전히 다른 경로</b>다 — 사번으로 로그인하고, 토큰도 다른 테이블에 저장한다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String NOT_DELETED = "N";

    private final AdminAccountRepository adminRepository;
    private final AdminRefreshTokenRepository tokenRepository;
    private final com.saas.admin.auth.jwt.JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public AdminTokenResponse login(String empNo, String rawPassword, String ip, String userAgent) {
        // 삭제된 계정은 조회 자체가 되지 않는다.
        AdminAccount admin = adminRepository.findByEmpNoAndDeleted(empNo, NOT_DELETED)
                .orElseThrow(() -> {
                    audit(null, "ADMIN_LOGIN", AuditResult.FAILURE, "존재하지 않는 사번: " + empNo, ip, userAgent);
                    // 사번이 없는 것인지 비밀번호가 틀린 것인지 구분해 알려주지 않는다.
                    return new ApiException(ErrorCode.INVALID_ADMIN_CREDENTIALS);
                });

        if (admin.isLocked()) {
            audit(admin.getEmpNo(), "ADMIN_LOGIN", AuditResult.FAILURE, "잠긴 계정", ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            admin.onLoginFail();   // 5회 실패하면 15분 잠금
            audit(admin.getEmpNo(), "ADMIN_LOGIN", AuditResult.FAILURE, "비밀번호 불일치", ip, userAgent);
            throw new ApiException(ErrorCode.INVALID_ADMIN_CREDENTIALS);
        }
        if (!admin.isUsable()) {
            audit(admin.getEmpNo(), "ADMIN_LOGIN", AuditResult.FAILURE, "사용 불가 상태: " + admin.getStatus(), ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        admin.onLoginSuccess();
        audit(admin.getEmpNo(), "ADMIN_LOGIN", AuditResult.SUCCESS, null, ip, userAgent);
        return issueTokens(admin, ip, userAgent);
    }

    /**
     * 토큰 회전: 쓴 리프레시 토큰은 즉시 폐기하고 새로 발급한다.
     * 재발급 시점에 계정 상태를 다시 확인하므로, 그 사이 삭제·정지됐다면 여기서 막힌다.
     */
    @Transactional
    public AdminTokenResponse refresh(String rawToken, String ip, String userAgent) {
        AdminRefreshToken stored = tokenRepository.findByTokenHash(tokenProvider.hashRefreshToken(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

        if (!stored.isUsable()) {
            throw new ApiException(ErrorCode.EXPIRED_TOKEN);
        }
        stored.revoke();

        AdminAccount admin = adminRepository.findByEmpNoAndDeleted(stored.getEmpNo(), NOT_DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_DISABLED));
        if (!admin.isUsable()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        return issueTokens(admin, ip, userAgent);
    }

    @Transactional
    public void logout(AuthPrincipal principal) {
        revokeAll(principal.empNo());
    }

    /** 계정 삭제·정지 시에도 쓴다. 이걸 안 하면 이미 발급된 토큰이 최대 14일간 살아 있다. */
    @Transactional
    public void revokeAll(String empNo) {
        List<AdminRefreshToken> alive = tokenRepository.findByEmpNoAndRevokedAtIsNull(empNo);
        alive.forEach(AdminRefreshToken::revoke);
    }

    private AdminTokenResponse issueTokens(AdminAccount admin, String ip, String userAgent) {
        // 기본 비밀번호 상태면 그 사실이 토큰에 실린다 → 필터가 관리자 권한을 주지 않는다.
        boolean mustChange = admin.mustChangePassword();
        String accessToken = tokenProvider.createAdminAccessToken(admin.getEmpNo(), admin.getEmail(), mustChange);
        String refreshToken = tokenProvider.createRefreshToken();

        tokenRepository.save(AdminRefreshToken.issue(
                admin.getEmpNo(),
                tokenProvider.hashRefreshToken(refreshToken),
                tokenProvider.refreshTokenExpiry(),
                ip,
                userAgent));

        return new AdminTokenResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenValiditySeconds(),
                admin.getEmpNo(),
                admin.getName(),
                mustChange);
    }

    /**
     * audit_log.actor_user_id 는 BIGINT 라 사번 문자열을 담을 수 없다.
     * 관리자의 행위는 resource_id 에 사번을 남기고, actor_role 로 구분한다.
     */
    private void audit(String empNo, String action, AuditResult result, String message, String ip, String userAgent) {
        auditService.record(AuditLog.builder()
                .actorRole("PLATFORM_ADMIN")
                .action(action)
                .resourceType("ADMIN_ACCOUNT")
                .resourceId(empNo)
                .result(result)
                .message(message)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }
}
