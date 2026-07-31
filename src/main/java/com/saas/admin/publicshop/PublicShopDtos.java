package com.saas.admin.publicshop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 손님(무인증) 테이블 주문용 DTO. QR 로 들어온 손님이 쓰는 최소한의 화면 계약. */
public final class PublicShopDtos {

    private PublicShopDtos() {
    }

    /** QR 진입 시 화면 상단에 표시할 가게/테이블 정보. */
    public record ShopTableView(String shopName, String tenantCode,
                                Long tableId, String tableCode, String tableLabel, int seats, boolean active) {
    }

    /** 포장 QR 진입 시 — 가게명 + 현재 포장주문을 받는지 여부(false 면 '정지' 화면). */
    public record ShopTakeoutView(String shopName, String tenantCode, boolean takeoutAvailable) {
    }

    /** 주문 접수 결과(손님에게 보여줄 최소 정보). */
    public record OrderPlaced(Long orderId, String orderNo, int totalAmount, String status,
                              boolean paid, String paymentMethod) {
    }

    @Schema(description = "손님 선불 주문 요청 — 메뉴판에서 결제를 마친 뒤 보낸다.")
    public record PlaceOrderRequest(
            @Size(max = 300) String memo,
            @Schema(description = "결제 수단 CARD / KAKAO_PAY / NAVER_PAY", example = "CARD")
            @Size(max = 20) String paymentMethod,
            @Schema(description = "PG 결제키(실제 연동 시). 모의결제는 비워도 됨.")
            @Size(max = 200) String paymentKey,
            @NotEmpty(message = "주문 항목은 최소 1개입니다.") @Valid List<Line> items
    ) {
        public record Line(
                @Schema(description = "메뉴 ID") Long menuItemId,
                @NotBlank(message = "메뉴명은 필수입니다.") @Size(max = 100) String menuName,
                int unitPrice,
                @Positive(message = "수량은 1 이상입니다.") int quantity,
                @Size(max = 300) String optionsText
        ) {
        }
    }
}
