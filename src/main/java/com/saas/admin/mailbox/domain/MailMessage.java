package com.saas.admin.mailbox.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 콘솔 메일함의 메일 한 통.
 * <p>
 * <b>메일의 원본은 메일 서버(Maildir)에 있고, 이 테이블은 콘솔이 보여주기 위한 사본이다.</b>
 * 받은 메일은 IMAP 으로 읽어와 채우고, 보낸 메일은 보낼 때 직접 남긴다.
 * <p>
 * <b>소유자(ownerEmpNo)를 두는 이유.</b> 메일 서버에는 kanchenjunga.co.kr 로 오는 메일이
 * catch-all 로 한 계정(kcmail)에 모두 모인다. 관리자가 늘 때마다 서버에 계정을 만들지 않기 위해서다.
 * 대신 받는 주소({@code 260001@kanchenjunga.co.kr})의 앞부분을 보고 어느 관리자 것인지 여기서 가른다.
 * 사번과 맞지 않는 주소(contact@ 등)는 {@code null} 로 두어 <b>공용 메일함</b>이 된다.
 */
@Entity
@Table(name = "mail_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_mail_message__msgid", columnNames = {"message_id"}),
        indexes = {
                @Index(name = "ix_mail_message__box", columnList = "owner_emp_no, folder, sent_at"),
                @Index(name = "ix_mail_message__uid", columnList = "imap_uid")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mail_id")
    private Long id;

    /** 이 메일의 주인 사번. null 이면 공용 메일함이라 모든 관리자가 본다. */
    @Column(name = "owner_emp_no", length = 6)
    private String ownerEmpNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "folder", nullable = false, length = 20)
    private MailFolder folder;

    @Column(name = "from_address", nullable = false, length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    /** 받는 사람. 여러 명이면 쉼표로 잇는다(표시·검색 전용이라 정규화하지 않는다). */
    @Column(name = "to_address", nullable = false, length = 1000)
    private String toAddress;

    @Column(name = "cc_address", length = 1000)
    private String ccAddress;

    @Column(name = "subject", length = 500)
    private String subject;

    /** 본문. HTML 이 없는 메일은 평문을 그대로 넣고 plainText 로 표시한다. */
    @Lob
    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    private String contentHtml;

    /** 본문이 HTML 이 아니라 평문인가. 화면이 그대로 그릴지 이스케이프할지 가른다. */
    @Column(name = "plain_text", nullable = false)
    private boolean plainText;

    /**
     * 메일의 RFC Message-ID. 같은 메일을 두 번 넣지 않기 위한 열쇠다.
     * IMAP 동기화가 여러 번 돌아도 유니크 제약이 중복 저장을 막는다.
     */
    @Column(name = "message_id", length = 500)
    private String messageId;

    /** IMAP UID. 어디까지 읽어왔는지 표시하는 데 쓴다. 보낸 메일은 null. */
    @Column(name = "imap_uid")
    private Long imapUid;

    @Column(name = "unread", nullable = false)
    private boolean unread;

    @Column(name = "has_attachment", nullable = false)
    private boolean hasAttachment;

    /** 별표(중요) 표시. 기존 테이블에도 추가되도록 기본값 0 을 준다. */
    @Column(name = "starred", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean starred;

    /** 보낸/받은 시각. 목록 정렬 기준. */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MailAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** IMAP 으로 읽어온 받은 메일. */
    public static MailMessage received(String ownerEmpNo, String fromAddress, String fromName,
                                       String toAddress, String ccAddress, String subject,
                                       String body, boolean plainText,
                                       String messageId, Long imapUid, LocalDateTime sentAt) {
        MailMessage m = new MailMessage();
        m.ownerEmpNo = ownerEmpNo;
        m.folder = MailFolder.INBOX;
        m.fromAddress = fromAddress;
        m.fromName = fromName;
        m.toAddress = toAddress;
        m.ccAddress = ccAddress;
        m.subject = subject;
        m.contentHtml = body;
        m.plainText = plainText;
        m.messageId = messageId;
        m.imapUid = imapUid;
        m.unread = true;
        m.sentAt = sentAt == null ? LocalDateTime.now() : sentAt;
        return m;
    }

    /** 콘솔에서 쓴 메일. 보낸편지함 또는 임시보관함에 들어간다. */
    public static MailMessage composed(String ownerEmpNo, MailFolder folder,
                                       String fromAddress, String fromName,
                                       String toAddress, String ccAddress,
                                       String subject, String html) {
        MailMessage m = new MailMessage();
        m.ownerEmpNo = ownerEmpNo;
        m.folder = folder;
        m.fromAddress = fromAddress;
        m.fromName = fromName;
        m.toAddress = toAddress;
        m.ccAddress = ccAddress;
        m.subject = subject;
        m.contentHtml = html;
        m.plainText = false;
        m.unread = false;
        m.sentAt = LocalDateTime.now();
        return m;
    }

    public void editDraft(String toAddress, String ccAddress, String subject, String html) {
        this.toAddress = toAddress;
        this.ccAddress = ccAddress;
        this.subject = subject;
        this.contentHtml = html;
        this.sentAt = LocalDateTime.now();
    }

    /** 임시보관함에 있던 것을 실제로 보냈을 때. */
    public void markSent() {
        this.folder = MailFolder.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markRead() {
        this.unread = false;
    }

    /** 별표(중요) 표시를 켜고 끈다. */
    public void markStar(boolean on) {
        this.starred = on;
    }

    public void moveTo(MailFolder target) {
        this.folder = target;
    }

    public void addAttachment(MailAttachment attachment) {
        this.attachments.add(attachment);
        this.hasAttachment = true;
    }

    /** 이 메일을 이 사번이 볼 수 있는가. 공용함(owner=null)은 모두가 본다. */
    public boolean visibleTo(String empNo) {
        // 자기 사번으로 온(또는 자기가 작성한) 메일만 볼 수 있다. 공용(owner null)은 각자 함에 안 보인다.
        return ownerEmpNo != null && ownerEmpNo.equals(empNo);
    }
}
