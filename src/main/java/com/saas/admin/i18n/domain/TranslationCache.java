package com.saas.admin.i18n.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 번역 캐시 — 원문(대개 한글)을 대상 언어로 한 번 번역해 저장해 둔다.
 * 같은 문장은 다시 외부 번역 API 를 부르지 않고 여기서 바로 돌려준다.
 * (source_lang, target_lang, source_hash) 로 유일. source_hash 는 원문의 SHA-256(hex).
 */
@Entity
@Table(name = "translation_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_translation__key",
                columnNames = {"source_lang", "target_lang", "source_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_lang", nullable = false, length = 8)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 8)
    private String targetLang;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TranslationCache of(String sourceLang, String targetLang, String sourceHash,
                                      String sourceText, String translatedText) {
        TranslationCache t = new TranslationCache();
        t.sourceLang = sourceLang;
        t.targetLang = targetLang;
        t.sourceHash = sourceHash;
        t.sourceText = sourceText;
        t.translatedText = translatedText;
        t.createdAt = LocalDateTime.now();
        return t;
    }
}
