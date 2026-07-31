package com.saas.admin.tenant.waitlist;

import com.saas.admin.tenant.waitlist.domain.WaitlistEntry;
import com.saas.admin.tenant.waitlist.domain.WaitlistStatus;
import com.saas.admin.tenant.waitlist.domain.WaitlistType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 예약/대기 관리 요청/응답 DTO 모음. */
public final class WaitlistDtos {

    private WaitlistDtos() {
    }

    @Schema(description = "예약/대기 접수. 예약은 테이블·예약일시·연락처 필수, 대기표는 연락처 필수. 순번은 서버가 매긴다.")
    public record WaitlistAddRequest(
            @Schema(description = "RESERVATION(예약) / WAITING(대기표)")
            @NotNull(message = "접수 유형은 필수입니다.")
            WaitlistType type,

            @Schema(description = "예약 대상 테이블 id(예약일 때 필수).")
            Long tableId,

            @Schema(description = "예약 일시(예약일 때 필수).")
            LocalDateTime reservedAt,

            @Size(max = 40, message = "이름은 40자를 넘을 수 없습니다.")
            String partyName,

            @Min(value = 1, message = "인원은 1명 이상이어야 합니다.")
            @Max(value = 99, message = "인원이 너무 많습니다.")
            int partySize,

            @Size(max = 30, message = "연락처가 너무 깁니다.")
            String phone,

            @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다.")
            String memo
    ) {
    }

    @Schema(description = "상태 변경. CALLED(호출) / SEATED(착석) 만 허용. 취소는 DELETE.")
    public record WaitlistStatusRequest(
            @NotNull(message = "상태는 필수입니다.")
            WaitlistStatus status
    ) {
    }

    public record WaitlistEntryView(Long id, WaitlistType type, int queueNo, LocalDateTime reservedAt,
                                    Long tableId, String tableLabel, Integer floorNo,
                                    String partyName, int partySize, String phone, String memo,
                                    WaitlistStatus status, LocalDateTime createdAt) {
        public static WaitlistEntryView of(WaitlistEntry w, String tableLabel, Integer floorNo) {
            return new WaitlistEntryView(w.getId(), w.getType(), w.getQueueNo(), w.getReservedAt(),
                    w.getTableId(), tableLabel, floorNo,
                    w.getPartyName(), w.getPartySize(), w.getPhone(), w.getMemo(), w.getStatus(), w.getCreatedAt());
        }
    }

    /** 예약 테이블 선택용 좌석 슬롯(배치 좌표 + 현재 점유/예약 표시). */
    public record TableSlot(Long tableId, int floorNo, String label, int seats, String kind,
                            int x, int y, int width, int height, boolean occupied, int reservedCount) {
    }

    /**
     * 예약/대기 화면 데이터 — 테이블 점유 현황 + 예약 목록 + 대기표 목록 + 배치도(예약 테이블 선택용).
     * reservations: 예약일시 오름차순. waiting: 순번 오름차순.
     */
    public record WaitlistBoard(int occupiedTables, int totalTables, boolean allFull,
                                int reservationCount, int waitingCount,
                                List<WaitlistEntryView> reservations, List<WaitlistEntryView> waiting,
                                int floorCount, int canvasW, int canvasH, List<TableSlot> tables) {
    }
}
