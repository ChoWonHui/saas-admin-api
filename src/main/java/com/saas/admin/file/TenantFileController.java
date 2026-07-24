package com.saas.admin.file;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 업체(사장님) 콘솔의 이미지 업로드 — 문의 첨부용. 로그인한 업체 사용자만.
 * 관리자 전용 업로드(/api/platform-admin/files)와 분리해 테넌트 토큰으로도 올릴 수 있게 한다.
 */
@Tag(name = "10. 파일 업로드(업체)", description = "업체 문의 이미지 업로드(S3).")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/files")
@RequiredArgsConstructor
public class TenantFileController {

    private final FileUploadService fileUploadService;
    private final ImageSearchService imageSearchService;

    @Operation(summary = "이미지 업로드", description = "이미지를 S3 에 올리고 CDN URL 을 반환한다. { \"url\": \"https://.../....png\" }")
    @PostMapping(path = "/images", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @RequestParam("file") MultipartFile file) {
        requireTenant(principal);
        return ResponseEntity.ok(Map.of("url", fileUploadService.upload(file)));
    }

    @Operation(summary = "URL 이미지 저장", description = "검색 결과 등 외부 이미지 URL 을 내려받아 S3 에 저장하고 CDN URL 을 반환한다.")
    @PostMapping("/from-url")
    public ResponseEntity<Map<String, String>> fromUrl(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @RequestBody Map<String, String> body) {
        requireTenant(principal);
        return ResponseEntity.ok(Map.of("url", fileUploadService.uploadFromUrl(body.get("url"))));
    }

    @Operation(summary = "이미지 검색(Pixabay)", description = "키워드로 무료 사진을 검색한다. [{ thumb, url }] 반환.")
    @GetMapping("/image-search")
    public ResponseEntity<List<Map<String, String>>> search(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @RequestParam String q,
                                                            @RequestParam(defaultValue = "1") int page) {
        requireTenant(principal);
        return ResponseEntity.ok(imageSearchService.search(q, page));
    }

    private void requireTenant(AuthPrincipal principal) {
        if (principal == null || principal.isAdmin() || !principal.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
    }
}
