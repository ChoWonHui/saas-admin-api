package com.saas.admin.order.stats;

import java.util.List;

/** 매출 통계 응답 DTO. 결제 완료(취소 제외) 주문 기준. */
public final class StatsDtos {

    private StatsDtos() {
    }

    /** 하루치 매출/건수. */
    public record DailyPoint(String date, long amount, long count) {
    }

    /** 결제수단별 매출/건수. method 는 코드(CARD/KAKAO_PAY…), label 은 한글. */
    public record MethodStat(String method, String label, long amount, long count) {
    }

    /** 메뉴별 판매 수량/매출. */
    public record MenuStat(String menuName, long quantity, long amount) {
    }

    /** 결제 목록 한 건. at=결제(생성) 일시(yyyy-MM-dd HH:mm), table=테이블/포장, method=결제수단(한글). */
    public record PaymentRow(Long orderId, String orderNo, String at, String table, long amount, String method, String status) {
    }

    /**
     * 기간 매출 통계.
     * totalRevenue 총매출, orderCount 결제 건수, avgOrder 객단가(평균),
     * daily 일별 시계열(빈 날 0 포함), methods 결제수단별, topMenus 인기 메뉴 TOP.
     */
    public record OrderStats(String from, String to,
                             long totalRevenue, long orderCount, long avgOrder,
                             List<DailyPoint> daily, List<MethodStat> methods, List<MenuStat> topMenus,
                             List<PaymentRow> payments) {
    }
}
