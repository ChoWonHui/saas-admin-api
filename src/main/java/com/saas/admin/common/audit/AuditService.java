package com.saas.admin.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 감사 로그는 본 트랜잭션과 분리해서(REQUIRES_NEW) 남긴다.
     * 로그인 실패처럼 <b>본 트랜잭션이 롤백되는 경우에도 기록은 남아야</b> 하기 때문이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // 감사 로그 적재 실패가 본 요청을 깨뜨려서는 안 된다.
            log.error("감사 로그 적재 실패: action={}", auditLog.getAction(), e);
        }
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return null;
        }
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
