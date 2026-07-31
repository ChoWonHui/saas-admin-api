package com.saas.admin.platformad;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 플랫폼 광고 배너 — 본사가 관리(get/save), 손님이 조회(publicView). 단일 행. */
@Service
@RequiredArgsConstructor
public class PlatformAdService {

    private final PlatformAdRepository repository;

    public record AdView(String imageUrl, String link, String text, boolean enabled) {
        static AdView of(PlatformAd a) {
            if (a == null) return new AdView(null, null, null, true);
            return new AdView(a.getImageUrl(), a.getLink(), a.getText(), a.isEnabled());
        }
    }

    public record AdSaveRequest(String imageUrl, String link, String text, boolean enabled) {
    }

    /** 본사 콘솔용 — 현재 광고(없으면 빈 값). */
    @Transactional(readOnly = true)
    public AdView get() {
        return AdView.of(repository.findSingle());
    }

    @Transactional
    public AdView save(AdSaveRequest req) {
        PlatformAd ad = repository.findSingle();
        if (ad == null) ad = repository.save(PlatformAd.blank());
        ad.update(req.imageUrl(), req.link(), req.text(), req.enabled());
        return AdView.of(ad);
    }

    /** 손님용 — 노출(enabled)이고 이미지가 있을 때만 콘텐츠, 아니면 enabled=false. */
    @Transactional(readOnly = true)
    public AdView publicView() {
        PlatformAd ad = repository.findSingle();
        if (ad == null || !ad.isEnabled() || ad.getImageUrl() == null) {
            return new AdView(null, null, null, false);
        }
        return AdView.of(ad);
    }
}
