package com.saas.admin.i18n;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public class I18nDtos {

    /** 번역 요청 — 대상 언어와 원문(대개 한글) 목록. */
    public record TranslateRequest(@NotBlank String targetLang, List<String> texts) {
    }

    /** 번역 결과 — 원문 → 번역문 맵. 실패/미지원 언어는 원문을 그대로 담는다(화면이 깨지지 않게). */
    public record TranslateResponse(String targetLang, Map<String, String> translations) {
    }
}
