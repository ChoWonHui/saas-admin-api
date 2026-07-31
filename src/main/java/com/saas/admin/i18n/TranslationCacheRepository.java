package com.saas.admin.i18n;

import com.saas.admin.i18n.domain.TranslationCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranslationCacheRepository extends JpaRepository<TranslationCache, Long> {

    /** 대상 언어에서 이미 번역해 둔 것들을 원문 해시로 한 번에 조회한다. */
    List<TranslationCache> findBySourceLangAndTargetLangAndSourceHashIn(
            String sourceLang, String targetLang, List<String> sourceHashes);
}
