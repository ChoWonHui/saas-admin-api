package com.saas.admin.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 관리자 실시간 알림 웹소켓 등록. 핸드셰이크의 토큰 인증은 핸들러에서 직접 처리한다. */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AdminNotifySocketHandler handler;
    private final TenantNotifySocketHandler tenantHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/admin-notify").setAllowedOriginPatterns("*");
        registry.addHandler(tenantHandler, "/ws/tenant-notify").setAllowedOriginPatterns("*");
    }
}
