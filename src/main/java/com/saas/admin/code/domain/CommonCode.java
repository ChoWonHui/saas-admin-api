package com.saas.admin.code.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 공통코드 상세 (예: JOB_GRADE 그룹의 SENIOR = 대리).
 * <p>
 * 코드값은 <b>발급 후 바꾸지 않는다</b> — 다른 테이블이 이 값을 저장한다. 이름(라벨)만 바꾼다.
 * 쓰지 않게 된 코드는 지우기보다 {@code use_yn = 'N'} 으로 내린다 — 기존 데이터가 이 코드를
 * 들고 있을 수 있어서, 선택지에서만 빠지고 과거 데이터 표시는 유지된다.
 */
@Entity
@Table(name = "common_code",
        uniqueConstraints = @UniqueConstraint(name = "uk_common_code__group_code", columnNames = {"group_code", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_code", nullable = false)
    private CodeGroup group;

    /** 코드값. 대문자/숫자/언더스코어. 생성 후 불변. */
    @Column(name = "code", nullable = false, length = 30, updatable = false)
    private String code;

    /** 화면에 보여줄 이름 (라벨). */
    @Column(name = "code_name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 'Y' = 선택지에 노출 / 'N' = 중지 (과거 데이터 표시는 유지). */
    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CommonCode create(CodeGroup group, String code, String name, int sortOrder) {
        CommonCode commonCode = new CommonCode();
        commonCode.group = group;
        commonCode.code = code;
        commonCode.name = name;
        commonCode.sortOrder = sortOrder;
        commonCode.useYn = "Y";
        return commonCode;
    }

    public void update(String name, Integer sortOrder, String useYn) {
        this.name = name;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (useYn != null) this.useYn = useYn;
    }

    /** 드래그앤드랍 순서 변경 — 순서만 바꾼다. */
    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getGroupCode() {
        return group.getGroupCode();
    }

    public boolean isActive() {
        return "Y".equals(useYn);
    }
}
