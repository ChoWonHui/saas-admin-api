package com.saas.admin.auth.jwt;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PLATFORM_ADMIN = "platformAdmin";
    private static final String CLAIM_SUBJECT_TYPE = "subjectType";
    private static final String CLAIM_EMP_NO = "empNo";
    private static final String CLAIM_MUST_CHANGE_PW = "mustChangePassword";

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;
    private final SecureRandom random = new SecureRandom();

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 은 32바이트 이상이어야 한다 (HS256 요구사항)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    /**
     * 업체 사용자(user_account)의 액세스 토큰.
     *
     * @param tenantId null 이면 테넌트 컨텍스트 없는 토큰 (업체 선택 전)
     */
    public String createAccessToken(Long userId, String email, Long tenantId, String roleCode, boolean platformAdmin) {
        return build(String.valueOf(userId), email, tenantId, roleCode, platformAdmin, SubjectType.USER, null);
    }

    /**
     * 관리자(admin_account)의 액세스 토큰. 테넌트 컨텍스트를 갖지 않는다.
     * <b>subject 는 사번</b>이다 — 관리자의 키가 사번이기 때문이다.
     * 주체 타입을 ADMIN 으로 박아, 업체 사용자 토큰과 절대 섞이지 않게 한다.
     */
    public String createAdminAccessToken(String empNo, String email, boolean mustChangePassword) {
        return build(empNo, email, null, null, true, SubjectType.ADMIN, empNo, mustChangePassword);
    }

    private String build(String subject, String email, Long tenantId, String roleCode,
                         boolean platformAdmin, SubjectType subjectType, String empNo) {
        return build(subject, email, tenantId, roleCode, platformAdmin, subjectType, empNo, false);
    }

    private String build(String subject, String email, Long tenantId, String roleCode,
                         boolean platformAdmin, SubjectType subjectType, String empNo,
                         boolean mustChangePassword) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValiditySeconds * 1000);

        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_ROLE, roleCode)
                .claim(CLAIM_PLATFORM_ADMIN, platformAdmin)
                .claim(CLAIM_SUBJECT_TYPE, subjectType.name())
                .claim(CLAIM_EMP_NO, empNo)
                .claim(CLAIM_MUST_CHANGE_PW, mustChangePassword)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Number tenantId = claims.get(CLAIM_TENANT_ID, Number.class);
            SubjectType subjectType = subjectType(claims);

            // 관리자 토큰의 subject 는 사번(문자열)이라 Long 으로 파싱하면 안 된다.
            // userId 는 업체 사용자 토큰에만 있다.
            if (subjectType == SubjectType.ADMIN) {
                return AuthPrincipal.admin(
                        claims.getSubject(),
                        claims.get(CLAIM_EMAIL, String.class),
                        Boolean.TRUE.equals(claims.get(CLAIM_MUST_CHANGE_PW, Boolean.class)));
            }

            return new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    tenantId == null ? null : tenantId.longValue(),
                    claims.get(CLAIM_ROLE, String.class),
                    Boolean.TRUE.equals(claims.get(CLAIM_PLATFORM_ADMIN, Boolean.class)),
                    subjectType,
                    null,
                    false
            );
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 주체 타입이 없는 토큰은 관리자 테이블이 생기기 전에 발급된 것이다.
     * 관리자 권한을 주면 안 되므로 USER 로 본다. (알 수 없으면 권한을 좁게)
     */
    private SubjectType subjectType(Claims claims) {
        String raw = claims.get(CLAIM_SUBJECT_TYPE, String.class);
        if (raw == null) {
            return SubjectType.USER;
        }
        try {
            return SubjectType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return SubjectType.USER;
        }
    }

    /** 리프레시 토큰은 의미 없는 난수다. 서명 토큰이 아니라 DB 대조로 검증한다. */
    public String createRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** DB 에는 원문이 아닌 SHA-256 해시만 저장한다. (002-06 참조) */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 해시 실패", e);
        }
    }

    public LocalDateTime refreshTokenExpiry() {
        return LocalDateTime.now().plusSeconds(refreshTokenValiditySeconds);
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }
}
