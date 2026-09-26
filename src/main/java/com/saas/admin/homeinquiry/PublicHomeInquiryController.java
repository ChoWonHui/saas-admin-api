package com.saas.admin.homeinquiry;

import com.saas.admin.homeinquiry.dto.HomeInquiryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 홈페이지 문의 접수 — <b>인증 없음</b>. kanchenjunga.co.kr / exprism.co.kr 의 /contact 폼이 부른다.
 * ({@code /api/public/**} 는 SecurityConfig 에서 permitAll)
 * <p>
 * 공개 API 라 두 가지를 지킨다.
 * <ul>
 *   <li>수신자 주소를 요청에서 받지 않는다 — 받으면 아무에게나 메일을 쏘는 중계기가 된다</li>
 *   <li>같은 IP 의 도배는 서비스에서 막는다 (10분 5건)</li>
 * </ul>
 */
@Tag(name = "24. 홈페이지 문의(무인증)", description = "회사 홈페이지 문의 폼 접수. 담당자에게 알림 메일이 나간다.")
@RestController
@RequestMapping("/api/public/home-inquiries")
@RequiredArgsConstructor
public class PublicHomeInquiryController {

    private final HomeInquiryService service;

    @Operation(summary = "문의 등록",
            description = "접수 후 공통코드 KCJG_CONTACT_EMAIL 에 등록된 주소로 알림 메일을 보낸다. "
                    + "메일이 실패해도 접수는 정상 처리된다(응답 200).")
    @PostMapping
    public ResponseEntity<CreateResponse> create(@Valid @RequestBody CreateRequest req,
                                                 HttpServletRequest http) {
        return ResponseEntity.ok(service.create(req, clientIp(http)));
    }

    /**
     * 디자인 시안 요청서(/design). 참고 이미지가 따라오므로 멀티파트로 받는다.
     * <p>
     * 예전에는 카페24의 {@code mail.php} 가 받았다. 사이트가 EC2(nginx, PHP 없음)로 옮겨오면서
     * 그 경로가 405 로 죽어 있었다.
     */
    @Operation(summary = "디자인 시안 요청서 접수",
            description = "이미지는 최대 5장. 접수 후 문의 게시판에 쌓이고 담당자에게 알림 메일이 나간다.")
    @PostMapping(value = "/design", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateResponse> createDesignRequest(
            @Valid @ModelAttribute DesignRequest req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest http) {
        return ResponseEntity.ok(service.createDesignRequest(req, images, clientIp(http)));
    }

    /**
     * 실제 접속 IP. 앞단이 nginx 프록시라 remoteAddr 은 항상 프록시 주소다 —
     * X-Forwarded-For 의 <b>맨 앞</b>(최초 클라이언트)을 쓴다.
     */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) return first.length() > 45 ? first.substring(0, 45) : first;
        }
        String remote = http.getRemoteAddr();
        return remote == null ? null : (remote.length() > 45 ? remote.substring(0, 45) : remote);
    }
}
