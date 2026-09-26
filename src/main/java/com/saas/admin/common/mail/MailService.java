package com.saas.admin.common.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SMTP 발송 한 곳. (Brevo 릴레이 — food-biz-api 와 같은 계정을 공유한다)
 * <p>
 * <b>메일 실패가 업무를 막지 않는다.</b> 이 클래스는 어떤 경우에도 예외를 밖으로 던지지 않고
 * 성공한 주소 목록만 돌려준다. 문의는 이미 DB 에 저장된 뒤이므로, 메일이 안 나가도 접수 자체는 살아 있다.
 * <p>
 * 키({@code spring.mail.password})가 비어 있으면 발송을 건너뛰고 로그만 남긴다 —
 * 그냥 clone 해서 띄운 개발 환경이 SMTP 인증 실패 로그로 뒤덮이지 않게 하기 위함이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${mail.enabled:true}")
    private boolean enabled;

    @Value("${mail.from:noreply@kanchenjunga.co.kr}")
    private String from;

    @Value("${mail.from-name:KANCHENJUNGA}")
    private String fromName;

    @Value("${spring.mail.password:}")
    private String smtpKey;

    /** 설정이 갖춰져 실제로 보낼 수 있는 상태인가. */
    public boolean isSendable() {
        return enabled && smtpKey != null && !smtpKey.isBlank();
    }

    /**
     * HTML 메일을 수신자별로 <b>한 통씩</b> 보낸다.
     * <p>
     * 한 통에 여러 To 를 담지 않는 이유: 주소 하나가 틀리면 그 통째로 거절돼 나머지도 못 받는다.
     * 수신자가 많아야 몇 명인 알림이라, 확실한 쪽을 택했다.
     *
     * @param replyTo 회신 주소(문의자 이메일). 담당자가 받은 메일에서 바로 답장할 수 있게 한다. 없으면 null.
     * @return 실제로 발송에 성공한 주소들
     */
    public List<String> sendHtml(List<String> to, String subject, String html, String replyTo) {
        List<String> sent = new ArrayList<>();
        if (to == null || to.isEmpty()) {
            log.warn("[메일] 수신자가 없어 발송을 건너뛴다. subject={}", subject);
            return sent;
        }
        if (!isSendable()) {
            log.warn("[메일] 발송이 꺼져 있거나 SMTP 키가 없다 — 로그로 대체한다. to={}, subject={}", to, subject);
            return sent;
        }

        for (String address : to) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper =
                        new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from, fromName);
                helper.setTo(address);
                if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(message);
                sent.add(address);
                log.info("[메일] 발송 성공 to={}, subject={}", address, subject);
            } catch (Exception e) {
                // 한 명이 실패해도 나머지는 계속 보낸다.
                log.error("[메일] 발송 실패 to={}, subject={}, error={}", address, subject, e.getMessage());
            }
        }
        return sent;
    }
}
