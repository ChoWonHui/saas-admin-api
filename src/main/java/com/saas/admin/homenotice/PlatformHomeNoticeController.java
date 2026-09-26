package com.saas.admin.homenotice;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.homenotice.dto.HomeNoticeDtos.*;
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
 * 홈페이지 공지사항 관리(내부 직원 콘솔).
 * 초안까지 전부 보이고, 공개 여부를 여기서 정한다.
 */
@Tag(name = "30. 홈페이지 공지(관리자)", description = "회사 사이트에 나갈 공지를 쓰고 공개한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/home-notices")
@RequiredArgsConstructor
public class PlatformHomeNoticeController {

    private final HomeNoticeService service;

    @Operation(summary = "공지 목록", description = "published=ALL|PUBLISHED|DRAFT")
    @GetMapping
    public ResponseEntity<PageResponse> list(@RequestParam(defaultValue = "ALL") String published,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listForAdmin(published, category, keyword, page, size));
    }

    @Operation(summary = "분류 선택지")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryOption>> categories() {
        return ResponseEntity.ok(service.categories());
    }

    @Operation(summary = "공지 상세(초안 포함)")
    @GetMapping("/{id}")
    public ResponseEntity<Detail> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getForAdmin(id));
    }

    @Operation(summary = "공지 등록", description = "published=false 로 보내면 사이트에 나가지 않는 초안이 된다.")
    @PostMapping
    public ResponseEntity<Detail> create(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody SaveRequest req) {
        return ResponseEntity.ok(service.create(empNo(principal), req));
    }

    @Operation(summary = "공지 수정")
    @PatchMapping("/{id}")
    public ResponseEntity<Detail> update(@PathVariable Long id, @Valid @RequestBody SaveRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @Operation(summary = "공지 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String empNo(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return principal.empNo();
    }
}
