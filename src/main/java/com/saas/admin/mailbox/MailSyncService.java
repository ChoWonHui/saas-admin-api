package com.saas.admin.mailbox;

import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.mailbox.domain.MailAttachment;
import com.saas.admin.mailbox.domain.MailMessage;
import com.saas.admin.mailbox.repository.MailMessageRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * 메일 서버(Postfix + Dovecot)의 메일함을 IMAP 으로 읽어 DB 로 옮긴다.
 * <p>
 * <b>왜 IMAP 인가.</b> kanchenjunga.co.kr 의 MX 는 우리 메일 서버를 가리킨다. 메일은 그 서버의
 * Maildir 에 쌓이고, 콘솔은 그것을 읽어 보여줘야 한다. (옆 프로젝트 food-biz 는 MX 를 Brevo 로
 * 돌려 웹훅으로 받지만, 그러면 메일이 우리 손을 떠나고 기존 메일함을 못 쓰게 된다)
 * <p>
 * <b>어디까지 읽었는지</b> 는 IMAP UID 로 기억한다. 그보다 큰 UID 만 새로 가져온다.
 * 메일함이 다시 만들어져 UID 가 되감기는 경우를 대비해, 저장 직전 Message-ID 유니크 제약이
 * 같은 메일의 중복 저장을 한 번 더 막는다.
 * <p>
 * 서버를 <b>읽기 전용</b>으로 연다. 콘솔에서 읽음 표시를 해도 메일 서버의 상태는 건드리지 않는다.
 * 기존 메일 클라이언트로 같은 메일함을 보던 사람이 영향을 받지 않게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSyncService {

    /** 한 번에 가져올 최대 통수. 처음 켤 때 수천 통을 한꺼번에 읽어 메모리를 터뜨리지 않게. */
    private static final int MAX_PER_RUN = 50;
    /** 첨부 한 개 상한. 넘으면 본문만 남기고 건너뛴다(메일은 이미 서버에 원본이 있다). */
    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    private final MailMessageRepository repository;
    private final AdminAccountRepository adminRepository;

    @Value("${mailbox.imap.enabled:false}")
    private boolean enabled;

    @Value("${mailbox.imap.host:}")
    private String host;

    @Value("${mailbox.imap.port:993}")
    private int port;

    @Value("${mailbox.imap.username:}")
    private String username;

    @Value("${mailbox.imap.password:}")
    private String password;

    /** 자체 서명 인증서를 쓰는 사설 메일 서버라 신뢰 검사를 끈다. */
    @Value("${mailbox.imap.trust-ssl:true}")
    private boolean trustSsl;

    @Value("${mailbox.domain:kanchenjunga.co.kr}")
    private String mailDomain;

    public boolean isConfigured() {
        return enabled && !host.isBlank() && !username.isBlank() && !password.isBlank();
    }

    /**
     * 주기적으로 새 메일을 가져온다. 실패해도 다음 주기에 다시 시도하므로 예외를 밖으로 내보내지 않는다.
     * (설정이 없으면 조용히 아무 일도 하지 않는다 - 그냥 clone 한 개발 환경에서 에러가 쌓이지 않게)
     */
    @Scheduled(fixedDelayString = "${mailbox.imap.sync-interval-ms:60000}",
            initialDelayString = "${mailbox.imap.initial-delay-ms:20000}")
    public void scheduledSync() {
        if (!isConfigured()) return;
        try {
            int n = sync();
            if (n > 0) log.info("[메일동기화] 새 메일 {}통을 가져왔다.", n);
        } catch (Exception e) {
            log.error("[메일동기화] 실패: {}", e.getMessage());
        }
    }

    /** 새 메일을 가져와 저장한다. 가져온 통수를 돌려준다. */
    @Transactional
    public int sync() throws MessagingException {
        if (!isConfigured()) return 0;

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "20000");
        if (trustSsl) {
            props.put("mail.imaps.ssl.trust", "*");
            props.put("mail.imaps.ssl.checkserveridentity", "false");
        }

        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;
        int saved = 0;
        try {
            store = session.getStore("imaps");
            store.connect(host, port, username, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            long lastUid = repository.maxImapUid();
            UIDFolder uidFolder = (UIDFolder) inbox;
            Message[] messages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);

            int handled = 0;
            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                // getMessagesByUID 는 새 메일이 없어도 마지막 한 통을 돌려준다. 직접 걸러낸다.
                if (uid <= lastUid) continue;
                if (handled++ >= MAX_PER_RUN) break;
                if (store(message, uid)) saved++;
            }
        } finally {
            closeQuietly(inbox, store);
        }
        return saved;
    }

    /** 한 통을 DB 에 넣는다. 이미 있으면 건너뛴다. 본문 인라인 이미지는 data URI 로 심고 첨부에서 뺀다. */
    private boolean store(Message message, long uid) {
        try {
            String messageId = firstHeader(message, "Message-ID");
            if (messageId == null || messageId.isBlank()) {
                // Message-ID 가 없는 메일도 있다. UID 로 대신 만들어 중복만 막는다.
                messageId = "<uid-" + uid + "@" + mailDomain + ">";
            }
            if (repository.existsByMessageId(messageId)) return false;

            Body body = new Body();
            List<Part> attachments = new ArrayList<>();
            List<Part> inlineImages = new ArrayList<>();
            walk(message, body, attachments, inlineImages);

            // 본문 인라인 이미지(Content-ID 로 cid: 참조)는 첨부로 두지 않고 본문 HTML 안에 data URI 로 심는다.
            // 심을 곳(HTML)이 없으면(순수 텍스트 메일) 잃지 않게 첨부로 남긴다.
            if (body.html != null) embedInlineImages(body, inlineImages);
            else attachments.addAll(inlineImages);

            boolean plain = body.html == null;
            String content = plain ? nullToEmpty(body.text) : body.html;

            InternetAddress from = firstAddress(message.getFrom());
            String toAddress = joinAddresses(message.getRecipients(Message.RecipientType.TO));
            String ccAddress = joinAddresses(message.getRecipients(Message.RecipientType.CC));
            Date sent = message.getSentDate() != null ? message.getSentDate() : message.getReceivedDate();
            LocalDateTime sentAt = sent == null ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(sent.toInstant(), ZoneId.systemDefault());
            MailMessage saved = MailMessage.received(
                    ownerOf(toAddress, ccAddress),
                    from == null ? "(알 수 없음)" : from.getAddress(),
                    from == null ? null : decode(from.getPersonal()),
                    nullToEmpty(toAddress), ccAddress,
                    decode(message.getSubject()),
                    content, plain, messageId, uid, sentAt);

            for (Part part : attachments) {
                byte[] bytes = readLimited(part);
                if (bytes == null) continue;
                saved.addAttachment(MailAttachment.of(saved, decode(part.getFileName()),
                        baseContentType(part.getContentType()), bytes));
            }
            repository.save(saved);
            return true;
        } catch (Exception e) {
            log.error("[메일동기화] 한 통 처리 실패 uid={}: {}", uid, e.getMessage());
            return false;
        }
    }

    /**
     * 받는 주소에서 이 메일의 주인을 찾는다.
     * {@code 260001@kanchenjunga.co.kr} 처럼 앞부분이 실제 사번이면 그 사람 것,
     * 아니면(contact@ 등) 공용 메일함(null)이다.
     */
    private String ownerOf(String toAddress, String ccAddress) {
        for (String raw : (nullToEmpty(toAddress) + "," + nullToEmpty(ccAddress)).split(",")) {
            String address = raw.trim();
            int at = address.indexOf('@');
            if (at <= 0) continue;
            String local = address.substring(0, at).trim();
            String domain = address.substring(at + 1).trim();
            if (!domain.equalsIgnoreCase(mailDomain)) continue;
            if (adminRepository.existsById(local)) return local;
        }
        return null;
    }

    // ===== MIME 해석 =====

    private static class Body {
        String text;
        String html;
    }

    /** 본문(텍스트/HTML)·첨부·인라인 이미지를 갈라 담는다. 멀티파트가 겹겹이 쌓인 메일도 재귀로 훑는다. */
    private void walk(Part part, Body body, List<Part> attachments, List<Part> inlineImages) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) walk(mp.getBodyPart(i), body, attachments, inlineImages);
            return;
        }
        // Content-ID 를 가진 이미지 = 본문에 cid: 로 박히는 인라인 이미지. 파일명이 있어도 첨부로 보내지 않는다.
        if (part.isMimeType("image/*") && contentIdOf(part) != null) {
            inlineImages.add(part);
            return;
        }
        String disposition = part.getDisposition();
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (part.getFileName() != null && !part.getFileName().isBlank());
        if (isAttachment) {
            attachments.add(part);
            return;
        }
        if (part.isMimeType("text/html")) {
            if (body.html == null) body.html = String.valueOf(part.getContent());
        } else if (part.isMimeType("text/plain")) {
            if (body.text == null) body.text = String.valueOf(part.getContent());
        }
    }

    /** 파트의 Content-ID(양끝 &lt;&gt; 제거). 없으면 null. */
    private static String contentIdOf(Part part) {
        try {
            String[] h = part.getHeader("Content-ID");
            if (h == null || h.length == 0 || h[0] == null) return null;
            String id = h[0].trim();
            if (id.startsWith("<") && id.endsWith(">")) id = id.substring(1, id.length() - 1);
            return id.isBlank() ? null : id;
        } catch (Exception e) {
            return null;
        }
    }

    /** 본문 HTML 의 {@code cid:<Content-ID>} 를 실제 이미지의 data URI 로 바꿔 본문에 심는다. */
    private void embedInlineImages(Body body, List<Part> inlineImages) {
        for (Part part : inlineImages) {
            try {
                String cid = contentIdOf(part);
                if (cid == null) continue;
                byte[] bytes = readLimited(part);
                if (bytes == null) continue; // 너무 크면 심지 않는다(원본 cid 는 그대로 남는다)
                String dataUri = "data:" + baseContentType(part.getContentType())
                        + ";base64," + Base64.getEncoder().encodeToString(bytes);
                body.html = body.html.replace("cid:" + cid, dataUri);
            } catch (Exception e) {
                log.warn("[메일동기화] 인라인 이미지 심기 실패 cid={}: {}", contentIdOf(part), e.getMessage());
            }
        }
    }

    private byte[] readLimited(Part part) {
        try (InputStream in = part.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0, read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_ATTACHMENT_BYTES) {
                    log.warn("[메일동기화] 첨부가 너무 커서 건너뛴다: {}", part.getFileName());
                    return null;
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("[메일동기화] 첨부 읽기 실패: {}", e.getMessage());
            return null;
        }
    }

    private static String firstHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return (values == null || values.length == 0) ? null : values[0];
    }

    private static InternetAddress firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return null;
        return addresses[0] instanceof InternetAddress ia ? ia : null;
    }

    private static String joinAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return null;
        List<String> list = new ArrayList<>();
        for (Address a : addresses) {
            if (a instanceof InternetAddress ia) list.add(ia.getAddress());
            else list.add(String.valueOf(a));
        }
        return String.join(", ", list);
    }

    /** 제목·파일명은 RFC 2047 로 인코딩돼 오는 경우가 많다(한글이 특히). */
    private static String decode(String value) {
        if (value == null) return null;
        try {
            return MimeUtility.decodeText(value);
        } catch (Exception e) {
            return value;
        }
    }

    /** "text/plain; charset=utf-8" 에서 앞부분만. */
    private static String baseContentType(String contentType) {
        if (contentType == null) return null;
        int semi = contentType.indexOf(';');
        return (semi < 0 ? contentType : contentType.substring(0, semi)).trim();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static void closeQuietly(Folder folder, Store store) {
        try {
            if (folder != null && folder.isOpen()) folder.close(false);
        } catch (Exception ignored) {
            // 닫기 실패는 다음 주기에 영향을 주지 않는다.
        }
        try {
            if (store != null && store.isConnected()) store.close();
        } catch (Exception ignored) {
            // 같음.
        }
    }
}
