package com.saas.admin.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 공지 에디터 이미지 업로드. 플랫폼 관리자(내부 직원)만 호출할 수 있다.
 * (SecurityConfig 에서 /api/platform-admin/** 는 PLATFORM_ADMIN 역할을 요구한다)
 *
 * <p>브라우저가 이미지를 멀티파트로 보내면 S3 에 올리고 공개(CDN) URL 을 돌려준다.
 * 프런트는 그 URL 을 에디터 &lt;img src&gt; 에 그대로 넣는다 — 본문 HTML 에 base64 를 박지 않는다.
 */
@Tag(name = "10. 파일 업로드", description = "공지 에디터 이미지 업로드(S3). 관리자 전용.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "에디터 이미지 업로드",
            description = "이미지를 S3 에 올리고 CDN URL 을 반환한다. { \"url\": \"https://.../....png\" }")
    @PostMapping(path = "/images", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.upload(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
