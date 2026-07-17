package com.saas.admin.code.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 공통코드 그룹 (예: JOB_GRADE = 직급).
 * <p>
 * 화면의 선택지(직급·직책·부서 …)를 코드로 관리해, 같은 값이 화면마다 다르게 적히는 것을 막는다.
 * 그룹코드는 영문 대문자 식별자라 <b>발급 후 바꾸지 않는다</b> — 다른 코드·화면이 이 값으로 그룹을 찾는다.
 * <p>
 * 플랫폼 공통 데이터다(테넌트 데이터가 아니다). 테이블은 Hibernate 가 만든다.
 */
@Entity
@Table(name = "common_code_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeGroup {

    /** 그룹 식별자. JOB_GRADE 처럼 대문자 스네이크. 생성 후 불변. */
    @Id
    @Column(name = "group_code", length = 30, updatable = false)
    private String groupCode;

    @Column(name = "group_name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CodeGroup create(String groupCode, String name, String description) {
        CodeGroup group = new CodeGroup();
        group.groupCode = groupCode;
        group.name = name;
        group.description = normalize(description);
        return group;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = normalize(description);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
