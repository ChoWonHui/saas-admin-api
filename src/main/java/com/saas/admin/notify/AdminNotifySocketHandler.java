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
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 관리자 실시간 알림 웹소켓. 업체가 문의/재문의를 남기면 접속 중인 모든 플랫폼 관리자에게 즉시 밀어준다.
 * 인증은 핸드셰이크 URL 의 ?token=<accessToken> 을 파싱해 플랫폼 관리자 토큰인지 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotifySocketHandler extends TextWebSocketHandler {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String token = tokenFromQuery(session.getUri());
            AuthPrincipal principal = (token == null) ? null : tokenProvider.parse(token);
            if (principal == null || !principal.platformAdmin()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
        } catch (Exception e) {
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignore) { /* noop */ }
            return;
        }
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /** 새 문의/재문의 → 접속 중인 관리자 전원에게 푸시. */
    public void pushNewInquiry(Long tenantId, Long inquiryId, String title, String tenantName) {
        broadcast(Map.of(
                "type", "NEW_INQUIRY",
                "tenantId", tenantId,
                "inquiryId", inquiryId,
                "title", title == null ? "" : title,
                "tenantName", tenantName == null ? "" : tenantName));
    }

    private synchronized void broadcast(Object payload) {
        if (sessions.isEmpty()) return;
        String json;
        try { json = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { log.warn("알림 직렬화 실패", e); return; }
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
