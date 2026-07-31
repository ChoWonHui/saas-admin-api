package com.saas.admin.platformad;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformAdRepository extends JpaRepository<PlatformAd, Long> {

    List<PlatformAd> findAllByOrderByIdAsc(PageRequest page);

    /** 단일 광고 행(첫 행). 없으면 null. */
    default PlatformAd findSingle() {
        List<PlatformAd> rows = findAllByOrderByIdAsc(PageRequest.of(0, 1));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
