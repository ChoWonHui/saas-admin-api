package com.saas.admin.mailbox.dto;

import com.saas.admin.mailbox.domain.MailAttachment;
import com.saas.admin.mailbox.domain.MailMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 메일함 DTO. */
public final class MailDtos {

    private MailDtos() {
    }

    // ---- 응답 ----

    public record AttachmentView(Long attachmentId, String filename, String contentType, long size) {
        public static AttachmentView from(MailAttachment a) {
            return new AttachmentView(a.getId(), a.getFilename(), a.getContentType(), a.getSize());
        }
    }

    public record Summary(
            Long mailId, String folder,
            String fromAddress, String fromName,
            String toAddress, String subject,
            boolean unread, boolean hasAttachment,
            /** 별표(중요) 표시 여부. */
            boolean starred,
            /** 공용 메일함으로 온 것인가. 목록에서 표시해 준다. */
            boolean shared,
            LocalDateTime sentAt
    ) {
        public static Summary from(MailMessage m) {
            return new Summary(
                    m.getId(), m.getFolder().name(),
                    m.getFromAddress(), m.getFromName(),
                    m.getToAddress(), m.getSubject(),
                    m.isUnread(), m.isHasAttachment(),
                    m.isStarred(),
                    m.getOwnerEmpNo() == null,
                    m.getSentAt());
        }
    }

    @Schema(description = "별표(중요) 토글")
    public record StarRequest(boolean starred) {
    }

    public record Detail(
            Long mailId, String folder,
            String fromAddress, String fromName,
            String toAddress, String ccAddress, String subject,
            String content, boolean plainText,
            boolean unread, boolean shared,
            List<AttachmentView> attachments,
            LocalDateTime sentAt
    ) {
        public static Detail from(MailMessage m) {
            return new Detail(
                    m.getId(), m.getFolder().name(),
                    m.getFromAddress(), m.getFromName(),
                    m.getToAddress(), m.getCcAddress(), m.getSubject(),
                    m.getContentHtml(), m.isPlainText(),
                    m.isUnread(), m.getOwnerEmpNo() == null,
                    m.getAttachments().stream().map(AttachmentView::from).toList(),
                    m.getSentAt());
        }
    }

    public record PageResponse(
            List<Summary> content, int page, int size,
            long totalElements, int totalPages
    ) {
    }

    /** 좌측 폴더 목록에 붙일 숫자. */
    public record FolderCount(String folder, String label, long total, long unread) {
    }

    /** 내 메일 주소와 표시 이름. 작성 화면이 "보내는 사람"에 보여준다. */
    public record MyAddress(String empNo, String name, String address) {
    }

    // ---- 요청 ----

    @Schema(description = "메일 작성(발송 또는 임시저장)")
    public record ComposeRequest(
            @NotBlank(message = "받는 사람은 필수입니다.")
            @Size(max = 1000) String to,
            @Size(max = 1000) String cc,
            @Size(max = 1000) String bcc,
            @Size(max = 500) String subject,
            String content,
            @Schema(description = "이어서 보낼 임시보관 메일 id. 없으면 새로 만든다.") Long draftId
    ) {
    }

    @Schema(description = "폴더 이동")
    public record MoveRequest(
            @NotBlank @Schema(description = "INBOX | SENT | DRAFT | TRASH") String folder
    ) {
    }
}
