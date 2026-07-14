package com.saas.admin.tenant.domain;

import java.util.regex.Pattern;

/**
 * slug 형식 규칙 (설계안 §2.1).
 * <pre>
 * - 소문자 영문 / 숫자 / 하이픈만
 * - 3 ~ 30자
 * - 하이픈으로 시작하거나 끝날 수 없음
 * - 연속 하이픈(--) 불가
 * - 한글 불가 (퍼센트 인코딩으로 URL·로그·SEO 품질 저하)
 * </pre>
 * 예약어 블랙리스트 검사는 DB(reserved_slug)를 봐야 하므로 여기서 하지 않는다.
 */
public final class SlugPolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Pattern CONSECUTIVE_HYPHEN = Pattern.compile("--");

    private SlugPolicy() {
    }

    public static boolean isValid(String slug) {
        if (slug == null) {
            return false;
        }
        if (slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH) {
            return false;
        }
        if (!FORMAT.matcher(slug).matches()) {
            return false;
        }
        return !CONSECUTIVE_HYPHEN.matcher(slug).find();
    }
}
