package com.saas.admin.code;

import com.saas.admin.code.domain.CodeGroup;
import com.saas.admin.code.domain.CommonCode;
import com.saas.admin.code.repository.CodeGroupRepository;
import com.saas.admin.code.repository.CommonCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기본 공통코드를 만든다. 관리자 화면의 부서/직급/직책 선택지가 첫 기동부터 비어 있지 않게 한다.
 * <p>
 * <b>비어 있을 때만</b> 심는다 — 관리자가 지우거나 바꾼 코드를 재기동이 되살리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeBootstrap implements ApplicationRunner {

    private final CodeGroupRepository groupRepository;
    private final CommonCodeRepository codeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 기본 조직 코드는 '완전히 비어 있을 때만' 심는다 — 관리자가 지운 코드를 재기동이 되살리지 않게.
        if (groupRepository.count() == 0) {
            seed("DEPARTMENT", "부서", "조직도의 부서코드가 여기서 온다",
                    List.of("HQ:본사", "PF:플랫폼본부", "MGMT:경영지원본부", "DEV:플랫폼개발팀",
                            "INFRA:인프라팀", "HR:인사팀", "CS:고객지원팀"));
            seed("JOB_GRADE", "직급", "사원 → 부장 순",
                    List.of("STAFF:사원", "SENIOR:대리", "MANAGER:과장", "DEPUTY:차장", "GENERAL:부장"));
            seed("JOB_TITLE", "직책", "보직 — 없을 수 있다",
                    List.of("PART_LEAD:파트장", "TEAM_LEAD:팀장", "DIRECTOR:실장"));
            // 영업장 테이블 좌석수 선택지. 편집기의 좌석 콤보박스가 여기서 온다(직접입력도 가능).
            seed("TABLE_SEATS", "테이블 좌석수", "영업장 테이블 배치의 좌석수 선택지",
                    List.of("2:2인", "4:4인", "6:6인", "8:8인"));
            log.info("[부트스트랩] 기본 공통코드를 생성했다. (부서/직급/직책/좌석수)");
        }

        // 주문관리(QR 주문) 공통코드 — 그룹이 없을 때만 그룹째 추가(멱등). 기존 설치에도 재기동 시 채워진다.
        // 상태값 자체는 화면 표시용이고, 실제 상태 전이 검증은 백엔드 Enum + 전이 정책이 담당한다.
        seedOrderingCodes();
    }

    /** MVP 주문관리 코드 그룹(스펙 10번의 20그룹). 이미 있으면 건너뛴다. */
    private void seedOrderingCodes() {
        seedIfAbsent("USER_ROLE", "사용자 역할", "화면 표시·기본 역할 구분용(실제 권한은 role/permission)",
                List.of("CUSTOMER:고객", "STAFF:홀 직원", "KITCHEN:주방 직원", "CASHIER:계산 담당",
                        "MANAGER:매니저", "OWNER:점주", "ADMIN:시스템 관리자"));
        seedIfAbsent("ORDER_TYPE", "주문 유형", "매장/포장/배달/예약",
                List.of("DINE_IN:매장 식사", "TAKEOUT:포장", "DELIVERY:배달", "RESERVATION:예약 주문"));
        seedIfAbsent("ORDER_CHANNEL", "주문 접수 경로", "주문이 들어온 경로",
                List.of("TABLE_QR:테이블 QR", "STAFF:직원 직접 등록", "POS:POS 등록",
                        "KIOSK:키오스크", "APP:앱", "WEB:웹 주문"));
        seedIfAbsent("ORDER_STATUS", "주문 상태", "주문 헤더 상태(전이 검증은 OrderStatus enum)",
                List.of("WAITING:주문 대기", "RECEIVED:주문 접수", "COOKING:조리 중", "READY:조리 완료",
                        "SERVING:서빙 중", "SERVED:서빙 완료", "PAYMENT_WAIT:결제 대기", "PARTIALLY_PAID:부분 결제",
                        "PAID:결제 완료", "CANCEL_REQUESTED:취소 요청", "CANCELLED:주문 취소", "CLOSED:주문 종료"));
        seedIfAbsent("ORDER_ITEM_STATUS", "주문 항목 상태", "메뉴별 상태(부분 조리/취소)",
                List.of("ORDERED:주문됨", "ACCEPTED:접수됨", "COOKING:조리 중", "READY:조리 완료",
                        "SERVED:서빙 완료", "CANCEL_REQUESTED:취소 요청", "CANCELLED:취소됨", "REFUNDED:환불됨"));
        seedIfAbsent("KITCHEN_STATUS", "주방 작업 상태", "주방(KDS) 작업 단위 상태",
                List.of("WAITING:주방 대기", "ACCEPTED:주방 접수", "COOKING:조리 중", "READY:조리 완료",
                        "HOLD:보류", "CANCELLED:취소"));
        seedIfAbsent("TABLE_STATUS", "테이블 상태", "테이블 자체 상태",
                List.of("EMPTY:빈 테이블", "SEATED:착석", "ORDERING:주문 중", "DINING:식사 중",
                        "PAYMENT_WAIT:결제 대기", "CLEANING:정리 중", "BLOCKED:사용 중지"));
        seedIfAbsent("TABLE_SESSION_STATUS", "테이블 세션 상태", "고객 이용 세션 상태(테이블 상태와 분리)",
                List.of("OPEN:이용 중", "PAYMENT_WAIT:결제 대기", "PAID:결제 완료",
                        "CLOSED:이용 종료", "CANCELLED:세션 취소"));
        seedIfAbsent("PAYMENT_STATUS", "결제 상태", "결제 집계 상태(전이 검증은 PaymentStatus enum)",
                List.of("READY:결제 준비", "REQUESTED:결제 요청", "PROCESSING:결제 처리 중", "APPROVED:승인 완료",
                        "PARTIALLY_APPROVED:부분 승인", "DECLINED:승인 거절", "CANCEL_REQUESTED:취소 요청",
                        "CANCELLED:승인 취소", "PARTIALLY_CANCELLED:부분 취소", "TIMEOUT:응답 시간 초과",
                        "UNKNOWN:결과 확인 필요", "FAILED:처리 실패"));
        seedIfAbsent("PAYMENT_METHOD", "결제 수단", "카드/현금/간편결제 등",
                List.of("CARD:신용·체크카드", "CASH:현금", "KAKAO_PAY:카카오페이", "TOSS_PAY:토스페이",
                        "NAVER_PAY:네이버페이", "BANK_TRANSFER:계좌이체", "POINT:포인트", "MIXED:복합 결제", "OTHER:기타"));
        seedIfAbsent("PAYMENT_CHANNEL", "결제 처리 경로", "결제를 처리한 경로",
                List.of("CARD_TERMINAL:카드단말기", "PG:온라인 PG", "POS:POS",
                        "MANUAL:수기 등록", "CASHIER:계산대", "MOBILE:모바일 결제"));
        seedIfAbsent("PAYMENT_TRANSACTION_TYPE", "결제 거래 유형", "결제/취소/환불 등 거래 종류",
                List.of("PAYMENT:결제", "CANCEL:전체 취소", "PARTIAL_CANCEL:부분 취소",
                        "REFUND:환불", "ADJUSTMENT:금액 조정"));
        seedIfAbsent("CANCEL_REASON", "취소 사유", "주문/결제 취소 사유(매장 확장 가능)",
                List.of("CUSTOMER_REQUEST:고객 요청", "OUT_OF_STOCK:품절", "ORDER_ERROR:주문 오류",
                        "DUPLICATE_ORDER:중복 주문", "STORE_REQUEST:매장 요청", "PAYMENT_FAILED:결제 실패", "OTHER:기타"));
        seedIfAbsent("SOLD_OUT_STATUS", "품절 상태", "메뉴 품절 처리 상태",
                List.of("AVAILABLE:판매 가능", "TEMPORARY_SOLD_OUT:일시 품절",
                        "DAILY_SOLD_OUT:당일 품절", "STOPPED:판매 중지"));
        seedIfAbsent("STAFF_CALL_TYPE", "직원 호출 유형", "손님 호출 종류(매장 확장 가능)",
                List.of("WATER:물 요청", "SIDE_DISH:반찬 요청", "ORDER_HELP:주문 문의",
                        "PAYMENT:결제 요청", "TABLE_CLEAN:테이블 정리", "ETC:기타 요청"));
        seedIfAbsent("STAFF_CALL_STATUS", "직원 호출 상태", "호출 처리 상태",
                List.of("REQUESTED:요청", "ACCEPTED:확인", "PROCESSING:처리 중", "COMPLETED:완료", "CANCELLED:취소"));
        seedIfAbsent("NOTIFICATION_TYPE", "알림 유형", "실시간 알림 종류",
                List.of("NEW_ORDER:신규 주문", "ORDER_CANCEL:주문 취소", "KITCHEN_READY:조리 완료",
                        "SERVING_REQUIRED:서빙 요청", "STAFF_CALL:직원 호출", "PAYMENT_REQUEST:결제 요청",
                        "PAYMENT_COMPLETE:결제 완료", "PAYMENT_ERROR:결제 오류", "SOLD_OUT:품절 알림", "SYSTEM:시스템 알림"));
        seedIfAbsent("NOTIFICATION_STATUS", "알림 처리 상태", "알림 전송/읽음 상태",
                List.of("CREATED:생성", "SENT:전송", "READ:읽음", "FAILED:실패", "CANCELLED:취소"));
        seedIfAbsent("SERVING_STATUS", "서빙 상태", "홀 서빙 작업 상태",
                List.of("WAITING:서빙 대기", "ASSIGNED:담당자 배정", "SERVING:서빙 중",
                        "COMPLETED:서빙 완료", "CANCELLED:서빙 취소"));
        seedIfAbsent("ACTOR_TYPE", "기록 발생 주체", "상태 변경·이력의 주체",
                List.of("CUSTOMER:고객", "STAFF:직원", "SYSTEM:시스템", "AGENT:결제 Agent", "ADMIN:관리자"));
    }

    private void seedIfAbsent(String groupCode, String name, String description, List<String> codes) {
        if (groupRepository.existsById(groupCode)) return;
        seed(groupCode, name, description, codes);
        log.info("[부트스트랩] 주문관리 공통코드 그룹 생성: {}", groupCode);
    }

    private void seed(String groupCode, String name, String description, List<String> codes) {
        CodeGroup group = groupRepository.save(CodeGroup.create(groupCode, name, description));
        int order = 1;
        for (String entry : codes) {
            String[] parts = entry.split(":", 2);
            codeRepository.save(CommonCode.create(group, parts[0], parts[1], order++));
        }
    }
}
