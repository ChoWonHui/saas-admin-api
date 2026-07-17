package com.saas.admin.org.repository;

import com.saas.admin.org.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** 트리 조립용 전체 조회. 조직은 수백 건을 넘지 않으므로 한 번에 다 읽어 메모리에서 조립한다. */
    List<Organization> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParentId(Long parentId);

    boolean existsByOrgCode(String orgCode);

    /** 수정 시 자기 자신을 뺀 중복 검사용. */
    boolean existsByOrgCodeAndIdNot(String orgCode, Long id);
}
