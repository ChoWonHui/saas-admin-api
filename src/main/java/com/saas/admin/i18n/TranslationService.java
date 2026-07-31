package com.saas.admin.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.admin.i18n.domain.TranslationCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * 메뉴 텍스트 자동 번역 — 원문(한글 가정)을 대상 언어로 번역하고 DB 에 캐시한다.
 * 키가 필요 없는 무료 제공자(MyMemory)를 쓰고, 한 번 번역한 문장은 다시 부르지 않는다.
 * 번역은 손님 화면 편의 기능이므로 **어떤 실패에도 예외를 던지지 않고 원문으로 폴백**한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private static final String SOURCE_LANG = "ko"; // 메뉴는 한글로 작성된다고 가정
    /** 지원 언어 → MyMemory 언어코드. ko 는 원문이라 번역하지 않는다. */
    private static final Map<String, String> LANGS = Map.of(
            "en", "en", "ja", "ja", "zh", "zh-CN", "es", "es");
    private static final int MAX_NEW_PER_REQUEST = 60; // 최초 전환 시 외부 호출 폭주 방지
    private static final int MAX_TEXT_LEN = 480;       // MyMemory 쿼리 길이 제한 대비

    private final TranslationCacheRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).build();

    // 트랜잭션을 걸지 않는다 — 각 캐시 저장은 아래에서 독립적으로 처리해,
    // 동시 요청이 같은 문장을 저장하다 하나가 중복키로 실패해도 나머지를 오염시키지 않게 한다.
    public Map<String, String> translate(String targetLang, List<String> texts) {
        String lang = targetLang == null ? "" : targetLang.trim().toLowerCase();
        Map<String, String> result = new LinkedHashMap<>();
        if (texts == null || texts.isEmpty()) return result;

        // 원문 그대로 쓰는 경우(한글·미지원 언어) — 그냥 원문을 돌려준다.
        String mmLang = LANGS.get(lang);
        if (SOURCE_LANG.equals(lang) || mmLang == null) {
            for (String t : texts) if (t != null && !t.isBlank()) result.put(t, t);
            return result;
        }

        // 중복 제거 + 해시
        LinkedHashMap<String, String> uniqToHash = new LinkedHashMap<>(); // 원문 → 해시
        for (String t : texts) {
            if (t == null || t.isBlank()) continue;
            uniqToHash.computeIfAbsent(t, TranslationService::sha256);
        }
        if (uniqToHash.isEmpty()) return result;

        // 캐시 조회
        List<String> hashes = new ArrayList<>(uniqToHash.values());
        Map<String, String> hashToTranslated = new HashMap<>();
        for (TranslationCache c : repository.findBySourceLangAndTargetLangAndSourceHashIn(
                SOURCE_LANG, lang, hashes)) {
            hashToTranslated.put(c.getSourceHash(), c.getTranslatedText());
        }

        int newCount = 0;
        for (Map.Entry<String, String> e : uniqToHash.entrySet()) {
            String text = e.getKey();
            String hash = e.getValue();
            String cached = hashToTranslated.get(hash);
            if (cached != null) {
                result.put(text, cached);
                continue;
            }
            // 미번역 — 호출 상한 안에서만 외부 번역, 넘치면 원문 유지(다음 요청에서 채워짐)
            if (newCount >= MAX_NEW_PER_REQUEST) {
                result.put(text, text);
                continue;
            }
            newCount++;
            String translated = callProvider(text, mmLang);
            if (translated == null || translated.isBlank() || translated.equals(text)) {
                result.put(text, text); // 폴백 — 실패는 저장하지 않는다(다음에 다시 시도)
            } else {
                result.put(text, translated);
                cache(SOURCE_LANG, lang, hash, text, translated); // 각각 독립 저장(중복키 무시)
            }
        }
        return result;
    }

    /**
     * 번역 한 건을 캐시에 저장한다. repository.save 는 자체 트랜잭션으로 돌아가므로,
     * 동시 요청이 같은 키를 먼저 넣어 중복키가 나도 여기서 삼키고 다른 저장에 영향을 주지 않는다.
     */
    private void cache(String sourceLang, String targetLang, String hash, String text, String translated) {
        try {
            repository.save(TranslationCache.of(sourceLang, targetLang, hash, text, translated));
        } catch (DataIntegrityViolationException dup) {
            // 다른 요청이 먼저 저장함 — 정상. 무시한다.
        } catch (Exception ex) {
            log.warn("translation cache save failed: {}", ex.getMessage());
        }
    }

    /** MyMemory 무료 번역 호출. 실패하면 null(→ 원문 폴백). */
    private String callProvider(String text, String mmLang) {
        try {
            String q = text.length() > MAX_TEXT_LEN ? text.substring(0, MAX_TEXT_LEN) : text;
            // 파이프(|)는 java.net.URI 에서 불법 문자라 %7C 로 인코딩한다(브라우저는 관대하지만 Java 는 엄격).
            String url = "https://api.mymemory.translated.net/get?q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8)
                    + "&langpair=" + SOURCE_LANG + "%7C" + mmLang;
            HttpResponse<String> res = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(4)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) return null;
            JsonNode root = objectMapper.readTree(res.body());
            int status = root.path("responseStatus").asInt(0);
            if (status != 200) return null; // 쿼터 초과 등
            String out = root.path("responseData").path("translatedText").asText("");
            if (out.isBlank()) return null;
            // 쿼터/오류 경고문이 번역문 자리에 오는 경우 폴백
            String upper = out.toUpperCase();
            if (upper.contains("MYMEMORY WARNING") || upper.contains("QUERY LENGTH LIMIT")
                    || upper.contains("INVALID LANGUAGE PAIR")) return null;
            return unescapeHtml(out);
        } catch (Exception e) {
            log.debug("translation call failed for '{}': {}", text, e.getMessage());
            return null;
        }
    }

    private static String unescapeHtml(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                              .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
