package com.saas.admin.mailbox.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메일 첨부파일.
 * <p>
 * 내용을 DB 에 그대로 담는다. S3 업로드 경로({@code FileUploadService})는 <b>이미지 전용</b>이라
 * 임의 형식의 첨부를 넣을 수 없고, 메일 첨부는 양이 많지 않아 이쪽이 단순하다.
 * (가게꾸미기 이미지도 같은 방식으로 DB 에 넣고 있다)
 */
@Entity
@Table(name = "mail_attachment", indexes = @Index(name = "ix_mail_attachment__mail", columnList = "mail_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mail_id", nullable = false)
    private MailMessage message;

    @Column(name = "filename", nullable = false, length = 300)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    public static MailAttachment of(MailMessage message, String filename, String contentType, byte[] content) {
        MailAttachment a = new MailAttachment();
        a.message = message;
        a.filename = (filename == null || filename.isBlank()) ? "attachment" : filename;
        a.contentType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        a.content = content;
        a.size = content == null ? 0 : content.length;
        return a;
    }
}
