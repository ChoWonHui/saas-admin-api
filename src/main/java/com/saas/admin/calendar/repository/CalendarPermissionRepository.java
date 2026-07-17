package com.saas.admin.calendar.repository;

import com.saas.admin.calendar.domain.CalendarPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CalendarPermissionRepository extends JpaRepository<CalendarPermission, Long> {

    List<CalendarPermission> findBySubjectTypeAndSubjectKey(String subjectType, String subjectKey);

    /**
     * 한 주체의 권한을 통째 교체할 때 먼저 지운다. 벌크 delete 로 즉시 실행 —
     * 파생 deleteBy 는 INSERT 가 DELETE 보다 먼저 flush 돼 유니크 제약이 깨진다(메뉴 권한에서 겪음).
     */
    @Modifying
    @Transactional
    @Query("delete from CalendarPermission p where p.subjectType = :type and p.subjectKey = :key")
    void deleteBySubjectTypeAndSubjectKey(@Param("type") String type, @Param("key") String key);
}
