package com.saas.admin.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** SecurityConfig 의 JwtAuthenticationFilter 가 읽는 헤더와 같아야 한다. */
    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme jwtScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        /api/auth/login 으로 받은 accessToken 을 넣는다. (Bearer 접두어는 자동으로 붙는다)

                        플랫폼 관리자는 로그인 토큰만으로 /api/platform-admin/** 을 쓸 수 있다.
                        업체 사용자는 /api/auth/select-tenant 로 업체를 고른 뒤 받은 토큰을 써야
                        테넌트 컨텍스트가 담긴다.
                        """);

        return new OpenAPI()
                .info(new Info()
                        .title("SaaS Admin API")
                        .version("v0.0.1")
                        .description("""
                                멀티테넌트 홈페이지·상담·예약 SaaS 백엔드.

                                현재 구현 범위: **플랫폼 통합 관리자** — 관리자 로그인, 업체 등록, 서비스 개설/중지.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER, jwtScheme));
    }
}
