package com.saas.admin.homenotice;

import com.saas.admin.homenotice.dto.HomeNoticeDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 회사 사이트 공지사항 — <b>인증 없음</b>. kanchenjunga.co.kr/notice 가 부른다.
 * 공개(published='Y')된 글만 나간다. 초안은 이 경로로 절대 나오지 않는다.
 */
@Tag(name = "29. 홈페이지 공지(무인증)", description = "회사 사이트 공지사항 목록·상세.")
@RestController
@RequestMapping("/api/public/home-notices")
@RequiredArgsConstructor
public class PublicHomeNoticeController {

    private final HomeNoticeService service;

    @Operation(summary = "공지 목록", description = "고정글 먼저, 그다음 공개일 최신순. category/keyword 로 좁힐 수 있다.")
    @GetMapping
    public ResponseEntity<PageResponse> list(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listPublic(category, keyword, page, size));
    }

    @Operation(summary = "분류 선택지", description = "공통코드 KCJG_NOTICE_CATEGORY 의 사용중인 코드.")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryOption>> categories() {
        return ResponseEntity.ok(service.categories());
    }

    @Operation(summary = "공지 상세", description = "조회수가 1 올라간다. 이전/다음 글도 함께 준다.")
    @GetMapping("/{id}")
    public ResponseEntity<Detail> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPublic(id));
    }
}
