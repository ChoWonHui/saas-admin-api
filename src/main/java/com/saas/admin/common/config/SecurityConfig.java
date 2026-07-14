package com.saas.admin.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.admin.auth.jwt.JwtAuthenticationFilter;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.common.error.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT 기반 무상태 API 다. 세션도 CSRF 토큰도 쓰지 않는다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Swagger UI / OpenAPI 문서.
                        // ⚠️ 운영에서는 열어두면 안 된다. API 구조와 스키마가 그대로 노출된다.
                        //    별도 프로파일에서 springdoc.api-docs.enabled=false 로 끄거나 이 항목을 제거할 것.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 플랫폼 관리자 전용. is_platform_admin 이 true 인 계정만 통과한다.
                        .requestMatchers("/api/platform-admin/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, ErrorCode.INVALID_TOKEN))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, ErrorCode.ACCESS_DENIED)))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res, ErrorCode code) throws java.io.IOException {
        res.setStatus(code.getStatus().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(res.getWriter(), ErrorResponse.of(code, code.getMessage()));
    }
}
