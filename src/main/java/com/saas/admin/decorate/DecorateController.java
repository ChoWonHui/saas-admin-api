package com.saas.admin.decorate;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.decorate.dto.DecorateDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 플랫폼 관리자 — 가게 꾸미기(store decorate) 카탈로그 관리.
 * 대분류(고정) → 소분류(분류) → 항목(선택지)를 추가·수정·삭제한다. 모든 업체 에디터가 공유한다.
 */
@Tag(name = "23. 가게 꾸미기 카탈로그(관리자)", description = "벽지·바닥·도구·벽배너·캐릭터 선택지와 분류를 관리한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/decorate")
@RequiredArgsConstructor
public class DecorateController {

    private final DecorateService decorateService;

    @Operation(summary = "카탈로그 전체(중지 포함)", description = "대분류·소분류·항목을 정렬 순으로 반환한다.")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> catalog() {
        return ResponseEntity.ok(decorateService.catalog());
    }

    @Operation(summary = "소분류 추가")
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decorateService.createCategory(req));
    }

    @Operation(summary = "소분류 수정", description = "키/대분류는 바꿀 수 없다.")
    @PatchMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateCategoryRequest req) {
        return ResponseEntity.ok(decorateService.updateCategory(id, req));
    }

    @Operation(summary = "소분류 삭제", description = "항목도 함께 삭제된다. 잠긴(시스템) 분류는 삭제할 수 없다.")
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        decorateService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "항목 추가")
    @PostMapping("/categories/{categoryId}/items")
    public ResponseEntity<ItemResponse> createItem(@PathVariable Long categoryId,
                                                   @Valid @RequestBody CreateItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decorateService.createItem(categoryId, req));
    }

    @Operation(summary = "항목 수정", description = "키는 바꿀 수 없다.")
    @PatchMapping("/items/{id}")
    public ResponseEntity<ItemResponse> updateItem(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateItemRequest req) {
        return ResponseEntity.ok(decorateService.updateItem(id, req));
    }

    @Operation(summary = "항목 삭제", description = "잠긴(코드로 그리는) 항목은 삭제할 수 없다 — 사용 여부만 바꾼다.")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        decorateService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "꾸미기 이미지 업로드", description = "머리·모자 그림 등을 DB 에 저장(S3 불필요). { url } 반환.")
    @PostMapping(value = "/images", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new ApiException(ErrorCode.FILE_EMPTY);
        Long id = decorateService.saveImage(file.getContentType(), file.getSize(), file.getBytes());
        return ResponseEntity.ok(Map.of("url", "/api/public/decorate/images/" + id));
    }
}
