package com.saas.admin.decorate;

import com.saas.admin.decorate.domain.DecorateImage;
import com.saas.admin.decorate.dto.DecorateDtos.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 무인증 — 가게 꾸미기 카탈로그(노출 항목만). 사장님 에디터(store decorate)와 손님 화면이 함께 쓴다.
 */
@Tag(name = "23-1. 가게 꾸미기 카탈로그(공개)", description = "노출 중인 벽지·바닥·도구·벽배너·캐릭터 선택지.")
@RestController
@RequestMapping("/api/public/decorate")
@RequiredArgsConstructor
public class PublicDecorateController {

    private final DecorateService decorateService;

    @Operation(summary = "카탈로그(노출 항목만)")
    @GetMapping("/catalog")
    public ResponseEntity<List<CategoryResponse>> catalog() {
        return ResponseEntity.ok(decorateService.activeCatalog());
    }

    @Operation(summary = "꾸미기 이미지", description = "머리·모자 그림 등을 서빙(무인증).")
    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        DecorateImage img = decorateService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS))
                .body(img.getData());
    }
}
