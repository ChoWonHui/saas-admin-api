package com.saas.admin.homeinquiry.dto;

import com.saas.admin.homeinquiry.domain.HomeInquiry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 홈페이지 문의 DTO. 등록은 무인증 공개 API 라, 받는 값을 여기서 좁게 못 박는다. */
public final class HomeInquiryDtos {

    private HomeInquiryDtos() {
    }

    // ---- 요청 (공개) ----

    /**
     * 홈페이지 /contact 폼이 보내는 값.
     * <p>
     * 수신자 주소는 <b>받지 않는다.</b> 받으면 그 순간 이 API 가 아무에게나 메일을 보내는
     * 스팸 중계기가 된다. 보낼 곳은 서버가 공통코드({@code KCJG_CONTACT_EMAIL})에서만 읽는다.
     */
    @Schema(description = "홈페이지 문의 등록 (무인증)")
    public record CreateRequest(
            @Schema(description = "KANCHENJUNGA(회사 문의) | EXPRISM(도입 문의). 비우면 KANCHENJUNGA",
                    example = "KANCHENJUNGA")
            String siteType,

            @NotBlank(message = "이름은 필수입니다.") @Size(max = 50) String name,
            @Size(max = 30) String phone,
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 150) String email,

            @Schema(description = "문의 제목. 도입 문의는 비워도 되며 매장명으로 자동 생성한다.")
            @Size(max = 200) String subject,
            @Size(max = 5000, message = "내용은 5000자를 넘을 수 없습니다.") String content,

            // EXPRISM 도입 문의 전용
            @Size(max = 100) String storeName,
            @Size(max = 50) String storeSize,
            @Size(max = 50) String openTiming,
            @Size(max = 20) List<@Size(max = 50) String> interests
    ) {
    }

    /** 등록 응답 — 접수 번호만 돌려준다. 메일 발송 여부는 방문자에게 알릴 것이 아니다. */
    public record CreateResponse(Long inquiryId) {
    }

    /**
     * 디자인 시안 요청서(/design). 멀티파트로 온다 - 참고 이미지가 함께 오기 때문이다.
     * 값은 화면(RequestForm)이 보내는 이름을 그대로 받는다.
     */
    @Schema(description = "디자인 시안 요청서 접수 (무인증, multipart)")
    public record DesignRequest(
            @NotBlank(message = "이름은 필수입니다.") @Size(max = 50) String name,
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 150) String email,
            @Size(max = 30) String phone,

            @Schema(description = "고른 시안. 예: 12 미니멀 그리드") @Size(max = 100) String concept,
            @Schema(description = "포인트 색") @Size(max = 30) String accent,
            @Schema(description = "넣고 싶은 메뉴(화면이 만든 문장)") @Size(max = 500) String menus,
            @Schema(description = "직접 입력한 로고·문구") @Size(max = 1000) String texts,
            @Schema(description = "요구사항") @Size(max = 3000) String note,

            @Schema(description = "봇 잡는 빈 칸. 값이 있으면 접수하지 않는다.", hidden = true)
            String honeypot
    ) {
    }

    // ---- 요청 (관리자) ----

    @Schema(description = "처리 상태 변경")
    public record StatusRequest(
            @NotBlank(message = "상태는 필수입니다.")
            @Schema(description = "NEW | IN_PROGRESS | DONE", example = "IN_PROGRESS") String status,
            @Size(max = 500) String memo
    ) {
    }

    // ---- 응답 (관리자) ----

    public record Summary(
            Long inquiryId, String siteType, String siteLabel,
            String name, String email, String phone, String subject,
            String status, String statusLabel, boolean notified,
            boolean hasImages,
            LocalDateTime createdAt
    ) {
        public static Summary from(HomeInquiry q) {
            return new Summary(
                    q.getId(), q.getSiteType().name(), q.getSiteType().getLabel(),
                    q.getName(), q.getEmail(), q.getPhone(), q.getSubject(),
                    q.getStatus().name(), q.getStatus().getLabel(), q.isNotified(),
                    !q.getImageUrls().isEmpty(),
                    q.getCreatedAt());
        }
    }

    public record Detail(
            Long inquiryId, String siteType, String siteLabel,
            String name, String email, String phone,
            String subject, String content,
            String storeName, String storeSize, String openTiming, String interests,
            String status, String statusLabel, String memo,
            boolean notified, String notifiedTo, LocalDateTime notifiedAt,
            String clientIp, List<String> imageUrls,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Detail from(HomeInquiry q) {
            return new Detail(
                    q.getId(), q.getSiteType().name(), q.getSiteType().getLabel(),
                    q.getName(), q.getEmail(), q.getPhone(),
                    q.getSubject(), q.getContent(),
                    q.getStoreName(), q.getStoreSize(), q.getOpenTiming(), q.getInterests(),
                    q.getStatus().name(), q.getStatus().getLabel(), q.getMemo(),
                    q.isNotified(), q.getNotifiedTo(), q.getNotifiedAt(),
                    q.getClientIp(), List.copyOf(q.getImageUrls()),
                    q.getCreatedAt(), q.getUpdatedAt());
        }
    }

    /** 목록 응답. Spring Page 를 그대로 내보내면 필드가 요란해서 필요한 것만 담는다. */
    public record PageResponse(
            List<Summary> content, int page, int size,
            long totalElements, int totalPages,
            long newCount, long inProgressCount, long doneCount
    ) {
    }
}
