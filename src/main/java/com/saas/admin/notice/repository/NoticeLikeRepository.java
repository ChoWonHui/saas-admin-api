package com.saas.admin.notice.repository;

import com.saas.admin.notice.domain.NoticeLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NoticeLikeRepository extends JpaRepository<NoticeLike, Long> {

    Optional<NoticeLike> findByNoticeIdAndEmpNo(Long noticeId, String empNo);

    boolean existsByNoticeIdAndEmpNo(Long noticeId, String empNo);

    long countByNoticeId(Long noticeId);

    /** 좋아요 누른 사람 목록(누른 순). 모달에서 보여준다. */
    List<NoticeLike> findByNoticeIdOrderByCreatedAtAsc(Long noticeId);

    /** 여러 공지의 좋아요 수를 한 번에(N+1 회피). 결과: [noticeId, count] 행들. */
    @Query("select l.noticeId, count(l) from NoticeLike l where l.noticeId in :ids group by l.noticeId")
    List<Object[]> countByNoticeIdIn(@Param("ids") List<Long> ids);

    /** 공지 삭제 시 좋아요도 함께 정리. */
    void deleteByNoticeId(Long noticeId);
}
