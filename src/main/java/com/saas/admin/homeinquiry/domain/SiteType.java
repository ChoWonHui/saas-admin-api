package com.saas.admin.homeinquiry.domain;

/**
 * 문의가 들어온 사이트. 두 도메인이 <b>같은 화면(saas-client-web 의 /contact)</b>을 쓰기 때문에,
 * 어느 쪽에서 온 문의인지는 서버가 값으로 받아 남긴다.
 *
 * <ul>
 *   <li>{@link #KANCHENJUNGA} — kanchenjunga.co.kr 의 "프로젝트 문의" (제목 + 내용)</li>
 *   <li>{@link #EXPRISM} — exprism.co.kr 의 "도입 문의" (매장명·규모·시기·관심기능)</li>
 * </ul>
 *
 * 회사(KANCHENJUNGA)와 제품(EXPRISM)은 다른 것이라 목록에서 섞이면 안 된다. 화면이 이 값으로 나눈다.
 */
public enum SiteType {
    KANCHENJUNGA("회사"),
    EXPRISM("EXPRISM"),
    /**
     * 디자인 시안 요청서(/design). 같은 회사 사이트에서 오지만 성격이 다르다 -
     * 고른 시안 번호와 색, 넣을 메뉴, 참고 이미지가 따라온다. 담당자도 처리도 달라서 나눠 둔다.
     * <p>
     * <b>이 값을 추가할 때 DB 도 같이 고쳤다.</b> Hibernate 가 만든 네이티브 ENUM 컬럼은
     * {@code ddl-auto: update} 로 바뀌지 않는다({@link HomeInquiryStatus} 주석 참조).
     * <pre>
     * ALTER TABLE home_inquiry MODIFY site_type ENUM('KANCHENJUNGA','EXPRISM','DESIGN') NOT NULL;
     * </pre>
     */
    DESIGN("디자인 요청");

    private final String label;

    SiteType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
