package com.saas.admin.mailbox;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.mailbox.domain.MailAttachment;
import com.saas.admin.mailbox.domain.MailFolder;
import com.saas.admin.mailbox.domain.MailMessage;
import com.saas.admin.mailbox.dto.MailDtos.*;
import com.saas.admin.mailbox.repository.MailMessageRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 관리자 콘솔 메일함.
 * <p>
 * 보내기는 Brevo SMTP 를 그대로 쓴다(알림 메일과 같은 경로). 발신 주소만 관리자마다 다르게
 * {@code {사번}@kanchenjunga.co.kr} 로 쓴다 - 받는 쪽에서 누가 보냈는지 바로 보이게 하기 위해서다.
 * 그 주소로 오는 답장은 메일 서버의 catch-all 이 받아 다시 이 메일함으로 들어온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailboxService {

    /** 보낼 때 첨부 총량 상한. Brevo SMTP 가 20MB 까지 받는다(SIZE 20971520). */
    private static final int MAX_TOTAL_ATTACHMENT = 15 * 1024 * 1024;

    private final MailMessageRepository repository;
    private final AdminAccountRepository adminRepository;
    private final JavaMailSender mailSender;

    @Value("${mailbox.domain:kanchenjunga.co.kr}")
    private String mailDomain;

    @Value("${mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${spring.mail.password:}")
    private String smtpKey;

    // ===== 조회 =====

    @Transactional(readOnly = true)
    public MyAddress myAddress(String empNo) {
        String name = adminRepository.findById(empNo).map(AdminAccount::getName).orElse(empNo);
        return new MyAddress(empNo, name, empNo + "@" + mailDomain);
    }

    @Transactional(readOnly = true)
    public List<FolderCount> folderCounts(String empNo) {
        return Arrays.stream(MailFolder.values())
                .map(f -> new FolderCount(f.name(), f.getLabel(),
                        repository.countBox(empNo, f), repository.countUnread(empNo, f)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse list(String empNo, String folder, String keyword, int page, int size) {
        Page<MailMessage> found = repository.findBox(
                empNo, parseFolder(folder), blankToNull(keyword),
                PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
        return new PageResponse(
                found.getContent().stream().map(Summary::from).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    /** 상세. 여는 순간 읽음으로 바꾼다. */
    @Transactional
    public Detail open(String empNo, Long id) {
        MailMessage m = findVisible(empNo, id);
        m.markRead();
        return Detail.from(m);
    }

    @Transactional(readOnly = true)
    public MailAttachment attachment(String empNo, Long mailId, Long attachmentId) {
        MailMessage m = findVisible(empNo, mailId);
        return m.getAttachments().stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.MAIL_NOT_FOUND));
    }

    // ===== 정리 =====

    @Transactional
    public void move(String empNo, Long id, String folder) {
        findVisible(empNo, id).moveTo(parseFolder(folder));
    }

    /** 별표(중요) 켜고 끄기. */
    @Transactional
    public void star(String empNo, Long id, boolean on) {
        findVisible(empNo, id).markStar(on);
    }

    /**
     * 삭제. 휴지통에 있으면 정말 지우고, 아니면 휴지통으로 옮긴다.
     * 한 번에 사라지지 않게 하려는 것이다 - 메일은 잘못 지우면 되돌릴 방법이 없다.
     */
    @Transactional
    public void delete(String empNo, Long id) {
        MailMessage m = findVisible(empNo, id);
        if (m.getFolder() == MailFolder.TRASH) repository.delete(m);
        else m.moveTo(MailFolder.TRASH);
    }

    // ===== 작성 =====

    /** 임시보관. 이미 있는 초안이면 덮어쓴다. */
    @Transactional
    public Detail saveDraft(String empNo, ComposeRequest req) {
        MailMessage draft = req.draftId() == null ? null : findVisible(empNo, req.draftId());
        if (draft == null) {
            MyAddress me = myAddress(empNo);
            draft = repository.save(MailMessage.composed(
                    empNo, MailFolder.DRAFT, me.address(), me.name(),
                    nullToEmpty(req.to()), req.cc(), req.subject(), req.content()));
        } else {
            draft.editDraft(nullToEmpty(req.to()), req.cc(), req.subject(), req.content());
            draft.moveTo(MailFolder.DRAFT);
        }
        return Detail.from(draft);
    }

    /**
     * 발송. 보내고 나서 보낸편지함에 남긴다.
     * <p>
     * 메일 서버가 아니라 Brevo SMTP 로 보낸다 - 우리 메일 서버는 25번만 열려 있어 외부로 릴레이하지
     * 않고, Brevo 는 이미 kanchenjunga.co.kr 로 DKIM 서명을 붙일 수 있게 인증돼 있다.
     */
    @Transactional
    public Detail send(String empNo, ComposeRequest req, List<MultipartFile> files) {
        if (req.to() == null || req.to().isBlank()) {
            throw new ApiException(ErrorCode.MAIL_RECIPIENT_REQUIRED);
        }
        if (!mailEnabled || smtpKey == null || smtpKey.isBlank()) {
            throw new ApiException(ErrorCode.MAIL_SEND_DISABLED);
        }

        MyAddress me = myAddress(empNo);
        long totalBytes = files == null ? 0
                : files.stream().filter(f -> f != null && !f.isEmpty()).mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TOTAL_ATTACHMENT) {
            throw new ApiException(ErrorCode.MAIL_ATTACHMENT_TOO_LARGE);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // 첨부가 있으면 multipart 로 열어야 한다.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, totalBytes > 0, StandardCharsets.UTF_8.name());
            helper.setFrom(me.address(), me.name());
            helper.setTo(splitAddresses(req.to()));
            if (req.cc() != null && !req.cc().isBlank()) helper.setCc(splitAddresses(req.cc()));
            // 숨은참조 — 받는 사람에게 주소가 노출되지 않는다. 초안에는 보관하지 않고 발송 시에만 싣는다.
            if (req.bcc() != null && !req.bcc().isBlank()) helper.setBcc(splitAddresses(req.bcc()));
            helper.setSubject(nullToEmpty(req.subject()));
            helper.setText(nullToEmpty(req.content()), true);
            if (files != null) {
                for (MultipartFile f : files) {
                    if (f == null || f.isEmpty()) continue;
                    helper.addAttachment(safeName(f.getOriginalFilename()), f::getInputStream, f.getContentType());
                }
            }
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[메일함] 발송 실패 from={}, to={}, error={}", me.address(), req.to(), e.getMessage(), e);
            throw new ApiException(ErrorCode.MAIL_SEND_FAILED);
        }

        // 초안에서 보냈으면 그 글을 보낸편지함으로 옮기고, 아니면 새로 남긴다.
        MailMessage sent;
        if (req.draftId() != null) {
            sent = findVisible(empNo, req.draftId());
            sent.editDraft(req.to(), req.cc(), req.subject(), req.content());
            sent.markSent();
        } else {
            sent = repository.save(MailMessage.composed(
                    empNo, MailFolder.SENT, me.address(), me.name(),
                    req.to(), req.cc(), req.subject(), req.content()));
        }
        if (files != null) {
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) continue;
                try {
                    sent.addAttachment(MailAttachment.of(sent, safeName(f.getOriginalFilename()),
                            f.getContentType(), f.getBytes()));
                } catch (Exception e) {
                    // 보내기는 이미 성공했다. 보낸편지함의 첨부 사본이 빠질 뿐이라 막지 않는다.
                    log.warn("[메일함] 보낸 첨부 보관 실패: {}", e.getMessage());
                }
            }
        }
        log.info("[메일함] 발송 완료 from={}, to={}", me.address(), req.to());
        return Detail.from(sent);
    }

    // ===== 내부 =====

    /** 내 것이거나 공용함인 메일만 연다. 남의 메일함을 id 로 찔러 보는 것을 막는다. */
    private MailMessage findVisible(String empNo, Long id) {
        MailMessage m = repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.MAIL_NOT_FOUND));
        if (!m.visibleTo(empNo)) throw new ApiException(ErrorCode.MAIL_NOT_FOUND);
        return m;
    }

    private static MailFolder parseFolder(String value) {
        try {
            return MailFolder.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** "a@x.com, b@y.com" 을 배열로. 빈 칸은 버린다. */
    private static String[] splitAddresses(String raw) {
        return Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /** 경로 구분자가 섞인 파일명이 오면 이름만 남긴다. */
    private static String safeName(String filename) {
        if (filename == null || filename.isBlank()) return "attachment";
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
