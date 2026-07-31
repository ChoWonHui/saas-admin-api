package com.saas.admin.decorate.domain;

/**
 * 가게 꾸미기(store decorate) 대분류. 고정 목록.
 * <ul>
 *   <li>{@code WALLPAPER} 벽지, {@code FLOOR} 바닥, {@code OBJECT} 도구, {@code BANNER} 벽배너</li>
 *   <li>{@code CHARACTER} 사장님 캐릭터 — 소분류가 부위(피부/머리스타일/머리색/옷색/모자)로 고정(locked)된다.</li>
 * </ul>
 */
public enum DecorateGroup {
    WALLPAPER("벽지"),
    FLOOR("바닥"),
    OBJECT("도구"),
    BANNER("벽배너"),
    CHARACTER("사장님 캐릭터");

    private final String label;

    DecorateGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
