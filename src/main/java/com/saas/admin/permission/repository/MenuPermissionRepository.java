package com.saas.admin.permission.repository;

import com.saas.admin.permission.domain.MenuPermission;
import com.saas.admin.permission.domain.PermissionSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MenuPermissionRepository extends JpaRepository<MenuPermission, Long> {

    List<MenuPermission> findBySubjectTypeAndSubjectKey(PermissionSubject subjectType, String subjectKey);

    /**
     * 한 주체의 권한을 통째로 교체할 때 기존 것을 먼저 지운다.
     * <p>
     * <b>벌크 delete 로 즉시 실행</b>한다. 파생 deleteBy 는 삭제를 영속성 컨텍스트에 큐잉하는데,
     * Hibernate 는 한 트랜잭션에서 INSERT 를 DELETE 보다 먼저 flush 해서 유니크 제약이 깨진다
     * (Duplicate entry). 벌크 delete 는 그 자리에서 SQL 이 나가므로 이후 INSERT 와 충돌하지 않는다.
     */
    @Modifying
    @Transactional
    @Query("delete from MenuPermission p where p.subjectType = :type and p.subjectKey = :key")
    void deleteBySubjectTypeAndSubjectKey(@Param("type") PermissionSubject type, @Param("key") String key);
}
