package com.saas.admin.homenotice.repository;

import com.saas.admin.homenotice.domain.HomeNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface HomeNoticeRepository extends JpaRepository<HomeNotice, Long> {

    /**
     * 방문자용 목록. 공개된 것만, 고정글 먼저, 그다음 공개일 최신순.
     * category 를 null 로 주면 전체.
     */
    @Query("""
            select n from HomeNotice n
            where n.published = 'Y'
              and (:category is null or n.category = :category)
              and (:keyword is null or lower(n.title) like lower(concat('%', :keyword, '%')))
            order by n.pinned desc, n.publishedAt desc, n.id desc
            """)
    Page<HomeNotice> findPublic(String category, String keyword, Pageable pageable);

    Optional<HomeNotice> findByIdAndPublished(Long id, String published);

    /**
     * 상세 화면의 이전/다음 글. 고정글 여부는 보지 않고 <b>공개일 순서</b>로만 잇는다 —
     * 읽는 사람이 기대하는 것은 시간 순서지, 우리가 위로 올려 둔 순서가 아니다.
     */
    Optional<HomeNotice> findFirstByPublishedAndPublishedAtLessThanOrderByPublishedAtDesc(
            String published, LocalDateTime at);

    Optional<HomeNotice> findFirstByPublishedAndPublishedAtGreaterThanOrderByPublishedAtAsc(
            String published, LocalDateTime at);

    /**
     * 관리자용 목록. 초안까지 전부 보인다.
     * published 를 null 로 주면 공개/비공개 모두.
     */
    @Query("""
            select n from HomeNotice n
            where (:published is null or n.published = :published)
              and (:category is null or n.category = :category)
              and (:keyword is null or lower(n.title) like lower(concat('%', :keyword, '%')))
            order by n.pinned desc, n.createdAt desc
            """)
    Page<HomeNotice> findForAdmin(String published, String category, String keyword, Pageable pageable);

    long countByPublished(String published);

    /**
     * 조회수만 1 올린다. 엔티티를 더티체킹으로 올리면 본문(LONGTEXT)까지 UPDATE 문에 실린다 —
     * 공개 화면이라 호출이 잦아서 그 비용이 그대로 쌓인다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update HomeNotice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    void increaseView(Long id);
}
