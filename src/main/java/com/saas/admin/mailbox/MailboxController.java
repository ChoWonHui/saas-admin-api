package com.saas.admin.mailbox;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.mailbox.domain.MailAttachment;
import com.saas.admin.mailbox.dto.MailDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 관리자 메일함. 내부 직원이 자기 주소({사번}@도메인)로 메일을 주고받는다.
 * <p>
 * 받은 메일은 사번이 맞는 사람에게만 보이고, 회사 대표 주소(contact@ 등)로 온 것은
 * 공용 메일함이 되어 관리자 누구나 본다.
 */
@Tag(name = "31. 메일함(관리자)", description = "콘솔에서 메일을 주고받는다. 받은 메일은 메일 서버에서 IMAP 으로 가져온다.")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RestController
@RequestMapping("/api/platform-admin/mailbox")
@RequiredArgsConstructor
public class MailboxController {

    private final MailboxService service;
    private final MailSyncService syncService;

    @Operation(summary = "내 메일 주소", description = "작성 화면의 '보내는 사람'에 쓴다.")
    @GetMapping("/me")
    public ResponseEntity<MyAddress> me(@AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(service.myAddress(empNo(p)));
    }

    @Operation(summary = "폴더별 개수", description = "좌측 폴더 목록의 전체/안읽음 숫자.")
    @GetMapping("/folders")
    public ResponseEntity<List<FolderCount>> folders(@AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(service.folderCounts(empNo(p)));
    }

    @Operation(summary = "메일 목록", description = "folder=INBOX|SENT|DRAFT|TRASH, keyword 는 제목·주소 검색")
    @GetMapping
    public ResponseEntity<PageResponse> list(@AuthenticationPrincipal AuthPrincipal p,
                                             @RequestParam(defaultValue = "INBOX") String folder,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(empNo(p), folder, keyword, page, size));
    }

    @Operation(summary = "메일 상세", description = "여는 순간 읽음으로 바뀐다.")
    @GetMapping("/{id}")
    public ResponseEntity<Detail> get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable Long id) {
        return ResponseEntity.ok(service.open(empNo(p), id));
    }

    @Operation(summary = "첨부 내려받기")
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> attachment(@AuthenticationPrincipal AuthPrincipal p,
                                               @PathVariable Long id,
                                               @PathVariable Long attachmentId) {
        MailAttachment a = service.attachment(empNo(p), id, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(a.getFilename(), StandardCharsets.UTF_8)   // 한글 파일명 대응
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(parseType(a.getContentType()))
                .contentLength(a.getSize())
                .body(new ByteArrayResource(a.getContent()));
    }

    @Operation(summary = "메일 보내기",
            description = "발신자는 로그인한 관리자의 사번 주소가 된다. 첨부는 multipart 의 files.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Detail> send(@AuthenticationPrincipal AuthPrincipal p,
                                       @Valid @ModelAttribute ComposeRequest req,
                                       @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(service.send(empNo(p), req, files));
    }

    @Operation(summary = "임시보관", description = "draftId 를 주면 그 초안을 덮어쓴다.")
    @PostMapping("/drafts")
    public ResponseEntity<Detail> saveDraft(@AuthenticationPrincipal AuthPrincipal p,
                                            @RequestBody ComposeRequest req) {
        return ResponseEntity.ok(service.saveDraft(empNo(p), req));
    }

    @Operation(summary = "폴더 이동")
    @PatchMapping("/{id}/folder")
    public ResponseEntity<Void> move(@AuthenticationPrincipal AuthPrincipal p,
                                     @PathVariable Long id,
                                     @Valid @RequestBody MoveRequest req) {
        service.move(empNo(p), id, req.folder());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "별표(중요) 토글")
    @PatchMapping("/{id}/star")
    public ResponseEntity<Void> star(@AuthenticationPrincipal AuthPrincipal p,
                                     @PathVariable Long id,
                                     @RequestBody StarRequest req) {
        service.star(empNo(p), id, req.starred());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "메일 삭제",
            description = "휴지통 밖이면 휴지통으로 옮기고, 휴지통 안이면 완전히 지운다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable Long id) {
        service.delete(empNo(p), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "지금 새 메일 가져오기",
            description = "주기 동기화를 기다리지 않고 즉시 IMAP 을 확인한다. 가져온 통수를 돌려준다.")
    @PostMapping("/sync")
    public ResponseEntity<Integer> syncNow(@AuthenticationPrincipal AuthPrincipal p) {
        empNo(p);
        if (!syncService.isConfigured()) throw new ApiException(ErrorCode.MAIL_IMAP_DISABLED);
        try {
            return ResponseEntity.ok(syncService.sync());
        } catch (Exception e) {
            log.error("[메일함] 수동 동기화 실패: {}", e.getMessage());
            throw new ApiException(ErrorCode.MAIL_SYNC_FAILED);
        }
    }

    private static MediaType parseType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String empNo(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return principal.empNo();
    }
}
