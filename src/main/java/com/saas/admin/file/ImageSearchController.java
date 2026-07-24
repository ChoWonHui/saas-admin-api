package com.saas.admin.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 무료 이미지 검색(Pixabay) 프록시. 키워드로 검색해 사진 목록을 돌려준다.
 * 키는 서버 설정(image-search.pixabay-key)에 두어 노출하지 않는다.
 * 프런트는 결과 중 하나를 고른 뒤 /files/from-url 로 S3 에 저장한다.
 */
@Tag(name = "10. 파일 업로드", description = "이미지 검색·저장.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/image-search")
@RequiredArgsConstructor
public class ImageSearchController {

    private final ImageSearchService imageSearchService;

    @Operation(summary = "이미지 검색", description = "키워드로 Pixabay 사진을 검색한다. [{ thumb, url }] 반환.")
    @GetMapping
    public ResponseEntity<List<Map<String, String>>> search(@RequestParam String q,
                                                            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(imageSearchService.search(q, page));
    }
}
