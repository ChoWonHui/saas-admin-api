package com.saas.admin.inquiry;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.inquiry.dto.InquiryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 플랫폼 관리자(내부 직원) 콘솔의 문의 관리. 전체 업체의 문의를 보고 답변한다.
 * (/api/platform-admin/** 는 SecurityConfig 에서 PLATFORM_ADMIN 역할을 요구한다)
 */
@Tag(name = "11. 문의(관리자)", description = "관리자가 모든 업체의 문의를 보고 답변한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/inquiries")
@RequiredArgsConstructor
public class PlatformInquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "문의 목록 (전체/상태별)", description = "status=ALL|OPEN|ANSWERED|CLOSED")
    @GetMapping
    public ResponseEntity<List<InquirySummary>> list(@RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(inquiryService.listForAdmin(status));
    }

    @Operation(summary = "문의 상세 + 답변")
    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetail> get(@PathVariable Long id) {
        return ResponseEntity.ok(inquiryService.getForAdmin(id));
    }

    @Operation(summary = "답변 등록")
    @PostMapping("/{id}/replies")
    public ResponseEntity<InquiryDetail> reply(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable Long id,
                                               @Valid @RequestBody ReplyCreateRequest req) {
        return ResponseEntity.ok(inquiryService.replyByAdmin(id, empNo(principal), req));
    }

    @Operation(summary = "문의 종료")
    @PostMapping("/{id}/close")
    public ResponseEntity<InquiryDetail> close(@PathVariable Long id) {
        return ResponseEntity.ok(inquiryService.close(id));
    }

    @Operation(summary = "문의 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inquiryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ===== 업체별 대화(채팅) =====

    @Operation(summary = "업체별 대화 목록", description = "문의별이 아니라 업체별로 하나의 대화방으로 묶는다. 미답변 업체 우선 표시용 needsReply 포함.")
    @GetMapping("/tenants")
    public ResponseEntity<List<TenantConvSummary>> tenantConversations() {
        return ResponseEntity.ok(inquiryService.listTenantConversations());
    }

    @Operation(summary = "업체 대화 전체(시간순)")
    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<ConvView> tenantConversation(@PathVariable Long tenantId) {
        return ResponseEntity.ok(inquiryService.getTenantConversation(tenantId));
    }

    @Operation(summary = "업체 대화에 메시지 보내기(관리자 답변)")
    @PostMapping("/tenants/{tenantId}/messages")
    public ResponseEntity<ConvView> sendToTenant(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable Long tenantId,
                                                 @Valid @RequestBody ReplyCreateRequest req) {
        return ResponseEntity.ok(inquiryService.sendAdminMessage(tenantId, empNo(principal), req));
    }

    private String empNo(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return principal.empNo();
    }
}
