package com.saas.admin.code.repository;

import com.saas.admin.code.domain.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    List<CommonCode> findAllByOrderBySortOrderAscIdAsc();

    List<CommonCode> findByGroupGroupCodeOrderBySortOrderAscIdAsc(String groupCode);

    boolean existsByGroupGroupCode(String groupCode);

    boolean existsByGroupGroupCodeAndCode(String groupCode, String code);

    /** 그룹+코드로 한 건. (예: DEPARTMENT + INFRA → "인프라팀") */
    Optional<CommonCode> findByGroupGroupCodeAndCode(String groupCode, String code);
}
