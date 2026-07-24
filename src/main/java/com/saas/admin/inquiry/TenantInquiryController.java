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
 * 업체(사장님) 콘솔의 문의 게시판. 로그인한 업체 사용자가 자기 가게 문의만 다룬다.
 * tenantId 는 토큰에서 꺼내며 프런트가 보낸 값은 신뢰하지 않는다.
 */
@Tag(name = "11. 문의(업체)", description = "업체 사용자가 문의를 올리고 답변을 확인한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/inquiries")
@RequiredArgsConstructor
public class TenantInquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "문의 목록 (우리 가게)")
    @GetMapping
    public ResponseEntity<List<InquirySummary>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(inquiryService.listForTenant(tenantId(principal)));
    }

    @Operation(summary = "문의 등록")
    @PostMapping
    public ResponseEntity<InquiryDetail> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody InquiryCreateRequest req) {
        return ResponseEntity.ok(inquiryService.createByTenant(tenantId(principal), principal.userId(), req));
    }

    @Operation(summary = "문의 상세 + 답변")
    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetail> get(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long id) {
        return ResponseEntity.ok(inquiryService.getForTenant(tenantId(principal), id));
    }

    @Operation(summary = "재문의(글타래) 등록")
    @PostMapping("/{id}/replies")
    public ResponseEntity<InquiryDetail> reply(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable Long id,
                                               @Valid @RequestBody ReplyCreateRequest req) {
        return ResponseEntity.ok(inquiryService.replyByTenant(tenantId(principal), id, principal.userId(), req));
    }

    /** 토큰에 테넌트 컨텍스트(업체)가 있어야 한다. 관리자 토큰·업체 미선택 토큰은 막는다. */
    private Long tenantId(AuthPrincipal principal) {
        if (principal == null || principal.isAdmin() || !principal.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return principal.tenantId();
    }
}
