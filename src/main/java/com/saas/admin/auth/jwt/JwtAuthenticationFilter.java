package com.saas.admin.auth.jwt;

import com.saas.admin.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null) {
            try {
                AuthPrincipal principal = tokenProvider.parse(token);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities(principal)));
            } catch (ApiException e) {
                // 토큰이 잘못됐으면 인증을 세우지 않고 넘긴다.
                // 보호된 경로면 EntryPoint 가 401 을 내고, 공개 경로면 그대로 통과한다.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> authorities(AuthPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // 관리자 권한은 admin_account 에서 발급된 토큰(subjectType=ADMIN)에만 준다.
        // platformAdmin 클레임만 보면, 옛 user_account 계정으로 발급된 토큰도 통과한다.
        //
        // 단, 기본 비밀번호 상태(mustChangePassword)면 관리자 권한을 주지 않는다.
        // 기본 비밀번호는 공개된 값이다 — 그 상태로는 비밀번호 변경 외에 아무것도 할 수 없어야 하고,
        // 화면을 우회해 API 를 직접 불러도 여기서 막힌다. (/api/platform-admin/** 은 403)
        if (principal.isAdmin() && !principal.mustChangePassword()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
        if (principal.roleCode() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.roleCode()));
        }
        return authorities;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
