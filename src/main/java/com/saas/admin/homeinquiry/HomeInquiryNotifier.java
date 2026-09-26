package com.saas.admin.homeinquiry;

import com.saas.admin.code.domain.CommonCode;
import com.saas.admin.code.repository.CommonCodeRepository;
import com.saas.admin.common.mail.MailService;
import com.saas.admin.homeinquiry.domain.HomeInquiry;
import com.saas.admin.homeinquiry.domain.SiteType;
import com.saas.admin.homeinquiry.repository.HomeInquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 홈페이지 문의가 들어오면 담당자들에게 알림 메일을 보낸다.
 * <p>
 * <b>수신자를 코드로 박지 않는 이유</b>: 담당자는 바뀐다. 공통코드 그룹
 * {@code KCJG_CONTACT_EMAIL} 에 등록된 주소로만 보내므로, 사람이 바뀌면 관리자 콘솔의
 * 공통코드 화면에서 코드를 추가하거나 사용여부를 'N' 으로 내리면 끝이다. 배포가 필요 없다.
 * <p>
 * <b>주소는 "코드명"에 적혀 있다.</b> 코드값(code)이 아니다 — 운영자가 그렇게 등록해 왔고
 * (예: {@code code='0'}, {@code code_name='jsj3216@naver.com'}), 코드값은 생성 후 불변이라
 * 주소를 고칠 수 없기 때문이다. 이름은 바꿀 수 있으니 주소가 이름에 있는 편이 오히려 맞다.
 * 다만 반대로 등록한 경우도 받아들이도록, 이름이 메일 주소가 아니면 코드값을 한 번 더 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeInquiryNotifier {

    /** 알림 받을 주소가 등록된 공통코드 그룹. */
    public static final String RECIPIENT_GROUP = "KCJG_CONTACT_EMAIL";

    /** 메일 주소로 볼 수 있는가만 본다. 공백·@·점만 확인하는 느슨한 검사다(RFC 검증이 목적이 아니다). */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+[.][^@\\s]+$");
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CommonCodeRepository codeRepository;
    private final HomeInquiryRepository inquiryRepository;
    private final MailService mailService;

    @Value("${mail.console-url:https://kanchenjunga.co.kr}")
    private String consoleUrl;

    /**
     * 알림 발송. 방문자를 기다리게 하지 않으려고 별도 스레드에서 돈다.
     * 실패해도 문의는 이미 저장돼 있으므로 예외를 밖으로 내보내지 않는다.
     * <p>
     * <b>커밋이 끝난 뒤에만 실행된다.</b> 접수 트랜잭션이 아직 커밋되지 않은 상태에서 돌면
     * 방금 저장한 문의를 못 찾는다 ({@link HomeInquiryCreatedEvent} 참조).
     * 커밋 뒤라 열린 트랜잭션이 없으므로 새로 하나 연다.
     */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInquiryCreated(HomeInquiryCreatedEvent event) {
        Long inquiryId = event.inquiryId();
        try {
            HomeInquiry q = inquiryRepository.findById(inquiryId).orElse(null);
            if (q == null) {
                log.warn("[문의알림] 대상이 없다. id={}", inquiryId);
                return;
            }

            List<String> recipients = recipients();
            if (recipients.isEmpty()) {
                log.warn("[문의알림] 공통코드 {} 에 사용중인 수신 주소가 없다. id={}", RECIPIENT_GROUP, inquiryId);
                return;
            }

            List<String> sent = mailService.sendHtml(
                    recipients, subjectOf(q), htmlOf(q), q.getEmail());
            q.markNotified(String.join(", ", sent));
        } catch (Exception e) {
            log.error("[문의알림] 처리 중 오류. id={}, error={}", inquiryId, e.getMessage(), e);
        }
    }

    /** 공통코드에서 읽은, 지금 사용중인 수신 주소들. */
    public List<String> recipients() {
        return codeRepository.findByGroupGroupCodeOrderBySortOrderAscIdAsc(RECIPIENT_GROUP).stream()
                .filter(CommonCode::isActive)
                .map(HomeInquiryNotifier::addressOf)
                .filter(a -> a != null)
                .distinct()
                .toList();
    }

    /** 코드명이 메일 주소면 그것을, 아니면 코드값을 본다. 둘 다 아니면 건너뛴다. */
    private static String addressOf(CommonCode code) {
        String name = code.getName() == null ? "" : code.getName().trim();
        if (EMAIL.matcher(name).matches()) return name;
        String value = code.getCode() == null ? "" : code.getCode().trim();
        if (EMAIL.matcher(value).matches()) return value;
        log.warn("[문의알림] 메일 주소로 볼 수 없는 코드는 건너뛴다. code={}, name={}", value, name);
        return null;
    }

    /**
     * 메일 제목. 앞에 종류를 붙여 받은 편지함에서 한눈에 갈리게 한다.
     * 단 도입 문의는 저장된 제목이 이미 {@code [EXPRISM 도입 문의] 매장명} 이라
     * 그대로 붙이면 말머리가 두 번 나온다 — 이미 대괄호로 시작하면 붙이지 않는다.
     */
    private String subjectOf(HomeInquiry q) {
        String subject = q.getSubject() == null ? "" : q.getSubject().trim();
        if (subject.startsWith("[")) {
            return "%s - %s".formatted(subject, q.getName());
        }
        return "[%s] %s - %s".formatted(kindOf(q), subject, q.getName());
    }

    /**
     * 알림 본문. 담당자가 메일만 보고 바로 연락할 수 있도록 연락처를 위에 둔다.
     * 방문자가 쓴 글은 그대로 보여주되 HTML 로 해석되지 않게 이스케이프한다.
     */
    private String htmlOf(HomeInquiry q) {
        StringBuilder rows = new StringBuilder();
        row(rows, "이름", q.getName());
        row(rows, "연락처", q.getPhone());
        row(rows, "이메일", q.getEmail());
        if (q.getSiteType() == SiteType.EXPRISM) {
            row(rows, "매장명", q.getStoreName());
            row(rows, "매장 규모", q.getStoreSize());
            row(rows, "도입 희망 시기", q.getOpenTiming());
            row(rows, "관심 기능", q.getInterests());
        }
        row(rows, "접수 일시", q.getCreatedAt() == null ? null : q.getCreatedAt().format(AT));

        String body = esc(q.getContent());
        if (body.isBlank()) body = "<span style=\"color:#888\">(내용 없음)</span>";
        else body = body.replace("\n", "<br>");

        // 첨부는 파일이 아니라 링크로 보낸다. 5MB 짜리 다섯 장을 실으면 받는 메일함이 거절한다.
        List<String> images = q.getImageUrls();
        if (!images.isEmpty()) {
            StringBuilder att = new StringBuilder(
                    "<p style=\"margin-top:16px\"><strong>첨부 이미지 %d장</strong></p><p>".formatted(images.size()));
            for (int i = 0; i < images.size(); i++) {
                String url = esc(images.get(i));
                att.append("<a href=\"%s\" style=\"margin-right:10px\">%d번 이미지</a>".formatted(url, i + 1));
            }
            body = body + att.append("</p>");
        }

        return """
                <div style="font-family:'Malgun Gothic',AppleSDGothicNeo,sans-serif;font-size:14px;color:#222;line-height:1.6">
                  <p style="margin:0 0 4px"><strong>%s</strong>가 접수되었습니다.</p>
                  <h2 style="margin:0 0 16px;font-size:18px">%s</h2>
                  <table cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:16px">
                    %s
                  </table>
                  <div style="border-top:1px solid #e5e5e5;padding-top:12px">%s</div>
                  <p style="margin-top:20px;font-size:12px;color:#888">
                    이 메일은 공통코드 %s 에 등록된 주소로 자동 발송되었습니다.
                    수신 대상은 관리자 콘솔 &gt; 설정 &gt; 공통코드에서 바꿀 수 있습니다.<br>
                    문의 목록: %s/console/9f7a3d81/home-inquiries
                  </p>
                </div>
                """.formatted(
                kindOf(q), esc(q.getSubject()), rows, body, RECIPIENT_GROUP, esc(consoleUrl));
    }

    /** 메일 제목과 본문 첫 줄에 같이 쓰는 문의 종류. */
    private static String kindOf(HomeInquiry q) {
        return switch (q.getSiteType()) {
            case EXPRISM -> "EXPRISM 도입 문의";
            case DESIGN -> "디자인 시안 요청";
            default -> "홈페이지 문의";
        };
    }

    private static void row(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append("""
                <tr>
                  <td style="padding:4px 16px 4px 0;color:#666;white-space:nowrap">%s</td>
                  <td style="padding:4px 0"><strong>%s</strong></td>
                </tr>
                """.formatted(esc(label), esc(value)));
    }

    /** 방문자가 넣은 값이 메일 본문에서 태그로 살아나지 않게 막는다. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
