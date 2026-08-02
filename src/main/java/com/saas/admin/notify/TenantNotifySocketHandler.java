package com.saas.admin.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.auth.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 업체(사장님) 실시간 알림 웹소켓. 관리자가 답변하면 그 업체에 접속 중인 세션에만 밀어준다.
 * 인증은 핸드셰이크 URL 의 ?token=<업체 토큰> 을 파싱해 tenantId 를 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantNotifySocketHandler extends TextWebSocketHandler {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> byTenant = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String token = tokenFromQuery(session.getUri());
            AuthPrincipal p = (token == null) ? null : tokenProvider.parse(token);
            if (p == null || p.platformAdmin() || p.tenantId() == null) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            session.getAttributes().put("tenantId", p.tenantId());
            byTenant.computeIfAbsent(p.tenantId(), k -> new CopyOnWriteArraySet<>()).add(session);
        } catch (Exception e) {
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignore) { /* noop */ }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object tid = session.getAttributes().get("tenantId");
        if (tid instanceof Long id) {
            Set<WebSocketSession> s = byTenant.get(id);
            if (s != null) { s.remove(session); if (s.isEmpty()) byTenant.remove(id); }
        }
    }

    /** 관리자 답변 → 해당 업체에 접속 중인 세션에만 푸시. */
    public void pushAdminReply(Long tenantId, Long inquiryId, String preview) {
        Set<WebSocketSession> sessions = byTenant.get(tenantId);
        if (sessions == null || sessions.isEmpty()) return;
        broadcast(sessions, Map.of(
                "type", "ADMIN_REPLY",
                "inquiryId", inquiryId == null ? 0 : inquiryId,
                "preview", preview == null ? "" : preview));
    }

    /** 손님 결제(신규 주문) → 그 업체 주문관리 화면에 실시간으로 밀어준다. */
    public void pushNewOrder(Long tenantId, Long orderId, String tableLabel, int amount, String orderType) {
        Set<WebSocketSession> sessions = byTenant.get(tenantId);
        if (sessions == null || sessions.isEmpty()) return;
        broadcast(sessions, Map.of(
                "type", "NEW_ORDER",
                "orderId", orderId == null ? 0 : orderId,
                "tableLabel", tableLabel == null ? "" : tableLabel,
                "amount", amount,
                "orderType", orderType == null ? "" : orderType));
    }

    private synchronized void broadcast(Set<WebSocketSession> sessions, Object payload) {
        String json;
        try { json = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { log.warn("업체 알림 직렬화 실패", e); return; }
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) s.sendMessage(msg);
                else sessions.remove(s);
            } catch (IOException e) { sessions.remove(s); }
        }
    }

    private String tokenFromQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String kv : uri.getQuery().split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && "token".equals(kv.substring(0, i))) {
                return URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
