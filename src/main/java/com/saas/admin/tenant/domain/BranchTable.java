package com.saas.admin.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영업장 테이블(또는 룸). 지점의 각 층 캔버스에 픽셀 좌표로 배치된다.
 * 좌표(x,y)·크기(w,h)는 편집기 캔버스 기준 픽셀이다. kind 는 TABLE / ROOM.
 * 배치는 통째로 저장(replace)하므로 별도 소프트삭제 없이, 저장 시 없는 것은 삭제한다.
 */
@Entity
@Table(name = "branch_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "table_id")
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /**
     * 공개 식별 토큰(QR 주문 URL 에 쓰인다). 재저장(배치 갱신)해도 바뀌지 않아야
     * 이미 인쇄·부착한 QR 이 계속 유효하다. 그래서 저장 시 code 기준으로 upsert 한다.
     */
    @Column(name = "code", nullable = false, length = 40, updatable = false)
    private String code;

    /** 몇 층에 있는지. 1, 2, 3 … */
    @Column(name = "floor_no", nullable = false)
    private int floorNo;

    /** 표시 이름. 예: "T1", "룸A". 비면 화면에서 좌석수로만 표기. */
    @Column(name = "label", length = 30)
    private String label;

    /** 좌석수. 2 / 4 / 6 / 8 … */
    @Column(name = "seats", nullable = false)
    private int seats;

    /** TABLE(테이블) / ROOM(룸). */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "pos_x", nullable = false)
    private int x;

    @Column(name = "pos_y", nullable = false)
    private int y;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    public static BranchTable of(Long branchId, String code, int floorNo, String label, int seats, String kind,
                                 int x, int y, int width, int height) {
        BranchTable t = new BranchTable();
        t.branchId = branchId;
        t.code = (code == null || code.isBlank()) ? java.util.UUID.randomUUID().toString() : code;
        t.floorNo = floorNo;
        t.label = (label == null || label.isBlank()) ? null : label;
        t.seats = seats;
        t.kind = "ROOM".equals(kind) ? "ROOM" : "TABLE";
        t.x = x;
        t.y = y;
        t.width = width;
        t.height = height;
        return t;
    }

    /** 배치 재저장 시 위치·좌석 등만 갱신한다(code·id 는 유지). */
    public void update(int floorNo, String label, int seats, String kind, int x, int y, int width, int height) {
        this.floorNo = floorNo;
        this.label = (label == null || label.isBlank()) ? null : label;
        this.seats = seats;
        this.kind = "ROOM".equals(kind) ? "ROOM" : "TABLE";
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
