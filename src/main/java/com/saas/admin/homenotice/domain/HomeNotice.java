package com.saas.admin.homenotice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 홈페이지 공지사항 — 관리자가 등록하고 <b>회사 사이트 방문자 누구나</b> 본다.
 * (kanchenjunga.co.kr/notice)
 * <p>
 * 이 프로젝트에는 공지 테이블이 셋이 된다. 대상이 다르기 때문에 나눠 둔 것이다.
 * <ul>
 *   <li>{@code notice} — 사내 공지. 내부 직원만. 댓글·좋아요가 붙는다</li>
 *   <li>{@code tenant_notice} — 업체 공지. 사장님 콘솔에 뜬다. 팝업 기능이 있다</li>
 *   <li>{@code home_notice} — 이것. 로그인 없는 외부 방문자에게 공개된다</li>
 * </ul>
 * 공개 대상이라 {@code published} 로 <b>초안과 공개를 가른다</b> — 쓰다 만 글이 사이트에
 * 나가면 되돌릴 수 없다. 공개 API 는 published='Y' 만 내보낸다.
 */
@Entity
@Table(name = "home_notice", indexes = {
        @Index(name = "ix_home_notice__published", columnList = "published, published_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "home_notice_id")
    private Long id;

    /**
     * 분류 코드값. 공통코드 {@code KCJG_NOTICE_CATEGORY} 의 코드값을 담는다.
     * 화면에 보일 이름(라벨)은 저장하지 않고 읽을 때 코드에서 찾는다 —
     * "공지"를 "안내"로 바꾸면 과거 글까지 같이 바뀌어야 맞기 때문이다.
     */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 리치 에디터 HTML. 사내 공지와 같은 에디터를 쓴다. */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 작성한 관리자 사번. */
    @Column(name = "author_emp_no", nullable = false, length = 6)
    private String authorEmpNo;

    /** 작성자 이름 스냅샷 — 퇴사해도 목록이 비지 않게. */
    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    /** 상단 고정. 'Y'/'N'. */
    @Column(name = "pinned", nullable = false, length = 1)
    private String pinned;

    /** 사이트 공개 여부. 'Y'/'N'. 'N' 은 초안이라 방문자에게 보이지 않는다. */
    @Column(name = "published", nullable = false, length = 1)
    private String published;

    /** 처음 공개한 시각. 목록의 날짜로 쓴다(작성일이 아니라). 비공개면 null. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static HomeNotice create(String category, String title, String content,
                                    String authorEmpNo, String authorName,
                                    boolean pinned, boolean published) {
        HomeNotice n = new HomeNotice();
        n.category = category;
        n.title = title;
        n.content = content;
        n.authorEmpNo = authorEmpNo;
        n.authorName = authorName;
        n.pinned = pinned ? "Y" : "N";
        n.published = published ? "Y" : "N";
        n.publishedAt = published ? LocalDateTime.now() : null;
        n.viewCount = 0;
        return n;
    }

    /**
     * 수정. 비공개였다가 공개로 바뀌는 순간에만 공개일시를 찍는다 —
     * 이미 공개된 글을 고쳤다고 날짜가 오늘로 밀리면 목록 순서가 뒤집힌다.
     */
    public void update(String category, String title, String content, boolean pinned, boolean published) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.pinned = pinned ? "Y" : "N";
        if (published && this.publishedAt == null) this.publishedAt = LocalDateTime.now();
        this.published = published ? "Y" : "N";
    }

    public void increaseView() {
        this.viewCount++;
    }

    public boolean isPublished() {
        return "Y".equals(published);
    }

    public boolean isPinned() {
        return "Y".equals(pinned);
    }
}
