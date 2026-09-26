package com.saas.admin.homeinquiry;

import com.saas.admin.homeinquiry.dto.HomeInquiryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 홈페이지 문의 관리(내부 직원 콘솔).
 * ({@code /api/platform-admin/**} 는 SecurityConfig 에서 PLATFORM_ADMIN 역할을 요구한다)
 * <p>
 * 답변은 이 화면에서 주고받지 않는다 — 담당자가 메일·전화로 처리하고 여기서는 상태만 남긴다.
 */
@Tag(name = "28. 홈페이지 문의(관리자)", description = "회사 홈페이지로 들어온 문의를 확인하고 처리 상태를 남긴다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/home-inquiries")
@RequiredArgsConstructor
public class PlatformHomeInquiryController {

    private final HomeInquiryService service;

    @Operation(summary = "문의 목록",
            description = "siteType=ALL|KANCHENJUNGA|EXPRISM, status=ALL|NEW|IN_PROGRESS|DONE")
    @GetMapping
    public ResponseEntity<PageResponse> list(@RequestParam(defaultValue = "ALL") String siteType,
                                             @RequestParam(defaultValue = "ALL") String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(siteType, status, page, size));
    }

    @Operation(summary = "문의 상세")
    @GetMapping("/{id}")
    public ResponseEntity<Detail> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Operation(summary = "처리 상태 변경", description = "NEW → IN_PROGRESS → DONE. 메모를 같이 남길 수 있다.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Detail> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return ResponseEntity.ok(service.changeStatus(id, req));
    }

    @Operation(summary = "문의 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "현재 알림 수신 주소",
            description = "공통코드 KCJG_CONTACT_EMAIL 에서 읽은, 지금 알림이 나가는 주소들. "
                    + "화면 상단에 보여줘 '누가 받는지' 를 확인할 수 있게 한다.")
    @GetMapping("/recipients")
    public ResponseEntity<List<String>> recipients() {
        return ResponseEntity.ok(service.recipients());
    }
}
