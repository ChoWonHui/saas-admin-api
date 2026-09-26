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

    /**
     * 입금 계좌 안내. 손님이 직접 이체할 때 필요한 값이다.
     * <p>
     * 요금제와 무관하게 내려간다. 손님 화면은 이것을 <b>메뉴 상세 시트</b>에서 보여준다 —
     * 주문이 잠긴 가게(FREE)는 이체가 유일한 결제 수단이고, 주문을 받는 가게(BASIC 이상)에서도
     * 계좌를 확인하고 싶어 하는 손님이 있기 때문이다.
     * (결제창의 '계좌이체' 수단은 기존 그대로 두고 여기서 건드리지 않는다)
     * <p>
     * 은행·계좌번호 중 하나라도 비면 null 로 내려가고, 화면은 안내 자체를 띄우지 않는다.
     */
    public record BankAccountView(String bankName, String accountNo, String accountHolder) {
        public boolean usable() {
            return bankName != null && !bankName.isBlank() && accountNo != null && !accountNo.isBlank();
        }
    }

    /**
     * 결제 화면 하단 푸터에 노출할 <b>사업자 정보</b>.
     * <p>
     * 전자상거래법(§10)이 요구하는 판매자 표기 — 상호·대표자·사업자등록번호·통신판매업신고번호·
     * 주소·연락처를 담는다. <b>가게 메인({@code /home})의 발행(published) 여부와 무관하게</b>
     * 언제나 내려간다. 결제하는 손님에게 판매 주체를 밝히는 것은 법적 의무이고, 사장님이 소개
     * 페이지를 아직 안 만들었다고 해서 빠져서는 안 되기 때문이다. (원천은 tenant 자체다)
     * <p>
     * 각 항목은 비어 있으면 null 로 내려가고, 화면은 값이 있는 줄만 그린다.
     */
    public record BusinessInfoView(String shopName, String ownerName, String businessNo,
                                   String mailOrderSalesNo, String phone, String email, String address) {
        /** 상호 외에 표시할 값이 하나라도 있는가. 전부 비면 화면이 푸터 자체를 생략한다. */
        public boolean hasDetail() {
            return notBlank(ownerName) || notBlank(businessNo) || notBlank(mailOrderSalesNo)
                    || notBlank(phone) || notBlank(email) || notBlank(address);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /** QR 진입 시 화면 상단에 표시할 가게/테이블 정보. */
    public record ShopTableView(String shopName, String tenantCode,
                                Long tableId, String tableCode, String tableLabel, int seats, boolean active,
                                /** 요금제가 주문을 허용하는가. false 면 손님앱은 메뉴판만 보여준다. */
                                boolean orderEnabled,
                                /** 입금 계좌. 등록돼 있지 않으면 null. */
                                BankAccountView account,
                                /** 결제 화면 푸터용 사업자 정보. */
                                BusinessInfoView business) {
    }

    /** 포장 QR 진입 시 — 가게명 + 현재 포장주문을 받는지 여부(false 면 '정지' 화면). */
    public record ShopTakeoutView(String shopName, String tenantCode, boolean takeoutAvailable,
                                  boolean orderEnabled, BankAccountView account,
                                  /** 결제 화면 푸터용 사업자 정보. */
                                  BusinessInfoView business) {
    }

    /** 택배 진입 시 — 가게명 + 현재 택배주문을 받는지 여부(false 면 '택배 미제공' 화면). */
    public record ShopParcelView(String shopName, String tenantCode, boolean parcelAvailable,
                                 boolean orderEnabled, BankAccountView account,
                                 BusinessInfoView business) {
    }

    @Schema(description = "손님 택배 주문 요청 — 배송지 + 주문 항목. 비로그인.")
    public record PlaceParcelOrderRequest(
            @Size(max = 300) String memo,
            @Schema(description = "결제 수단 CARD / KAKAO_PAY / NAVER_PAY", example = "CARD")
            @Size(max = 20) String paymentMethod,
            @Schema(description = "PG 결제키(실제 연동 시). 모의결제는 비워도 됨.")
            @Size(max = 200) String paymentKey,
            @Valid @jakarta.validation.constraints.NotNull(message = "배송지는 필수입니다.")
            com.saas.admin.order.dto.OrderDtos.Shipping shipping,
            @NotEmpty(message = "주문 항목은 최소 1개입니다.") @Valid List<PlaceOrderRequest.Line> items
    ) {
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
