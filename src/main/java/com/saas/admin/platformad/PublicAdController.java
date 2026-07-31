package com.saas.admin.platformad;

import com.saas.admin.platformad.PlatformAdService.AdView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 손님(무인증) — 전 매장 공통 광고 배너 조회. 미노출이면 enabled=false. */
@Tag(name = "23. 광고(무인증)", description = "손님 화면 하단에 노출할 플랫폼 광고.")
@RestController
@RequestMapping("/api/public/ad")
@RequiredArgsConstructor
public class PublicAdController {

    private final PlatformAdService service;

    @Operation(summary = "플랫폼 광고 조회")
    @GetMapping
    public ResponseEntity<AdView> get() {
        return ResponseEntity.ok(service.publicView());
    }
}
