package com.saas.admin.platformad;

import com.saas.admin.platformad.PlatformAdService.AdSaveRequest;
import com.saas.admin.platformad.PlatformAdService.AdView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 본사(플랫폼 관리자) — 전 매장 공통 광고 배너 관리. */
@Tag(name = "22. 플랫폼 광고(본사)", description = "본사가 전 매장 손님 화면 하단 광고 배너를 관리한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/ad")
@RequiredArgsConstructor
public class PlatformAdController {

    private final PlatformAdService service;

    @Operation(summary = "광고 배너 조회")
    @GetMapping
    public ResponseEntity<AdView> get() {
        return ResponseEntity.ok(service.get());
    }

    @Operation(summary = "광고 배너 저장")
    @PutMapping
    public ResponseEntity<AdView> save(@RequestBody AdSaveRequest req) {
        return ResponseEntity.ok(service.save(req));
    }
}
