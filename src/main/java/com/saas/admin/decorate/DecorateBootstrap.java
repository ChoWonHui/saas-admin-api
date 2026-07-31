package com.saas.admin.decorate;

import com.saas.admin.decorate.domain.DecorateCategory;
import com.saas.admin.decorate.domain.DecorateGroup;
import com.saas.admin.decorate.domain.DecorateItem;
import com.saas.admin.decorate.repository.DecorateCategoryRepository;
import com.saas.admin.decorate.repository.DecorateItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가게 꾸미기 기본 카탈로그를 심는다(프론트 iso.js 기본값과 동일).
 * <b>완전히 비어 있을 때만</b> 심는다 — 관리자가 지우거나 바꾼 항목을 재기동이 되살리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecorateBootstrap implements ApplicationRunner {

    private final DecorateCategoryRepository categoryRepository;
    private final DecorateItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            upgradeHairstyles();     // 이미 심어진 설치도 머리스타일 디자인 데이터로 최신화(멱등)
            upgradeHats();           // 모자도 동일하게
            upgradeCategoryIcons();  // 도구 분류 아이콘 백필
            upgradeUnlockTools();    // 도구(테이블/카운터/가벽) 편집 가능하게 잠금 해제
            return;
        }

        // ── 벽지 ──
        DecorateCategory wp = cat(DecorateGroup.WALLPAPER, "basic", "기본", 1, false);
        color(wp, "cream", "크림", "#efe4d2", 1);
        color(wp, "brick", "벽돌", "#c88b6a", 2);
        color(wp, "mint", "민트", "#cfeede", 3);
        color(wp, "sky", "하늘", "#cfe4f7", 4);
        color(wp, "pink", "핑크", "#f4d0dc", 5);
        color(wp, "navy", "네이비", "#3c4a63", 6);
        color(wp, "white", "화이트", "#f1f1f6", 7);

        // ── 바닥 ──
        DecorateCategory fl = cat(DecorateGroup.FLOOR, "basic", "기본", 1, false);
        color(fl, "wood", "원목", "#d8b487", 1);
        color(fl, "tile", "타일", "#e6e6ee", 2);
        color(fl, "marble", "대리석", "#edeff3", 3);
        color(fl, "green", "잔디", "#bfe0c4", 4);
        color(fl, "dark", "다크", "#4a4a58", 5);
        color(fl, "red", "레드", "#d3a0a0", 6);

        // ── 도구 (분류는 자체 아이콘을 가진다) ──
        DecorateCategory furn = cat(DecorateGroup.OBJECT, "furniture", "가구", 1, false); furn.fillIconIfEmpty("🪑");
        vector(furn, "table", "테이블", "table", "🍽️", 25, 1);
        sprite(furn, "chair", "의자", "🪑", 20, 2);
        sprite(furn, "sofa", "소파", "🛋️", 25, 3);
        DecorateCategory faci = cat(DecorateGroup.OBJECT, "facility", "시설", 2, false); faci.fillIconIfEmpty("☕");
        vector(faci, "counter", "카운터", "counter", "☕", 24, 1);
        sprite(faci, "shelf", "선반", "📚", 26, 2);
        sprite(faci, "door", "문", "🚪", 26, 3);
        DecorateCategory deco = cat(DecorateGroup.OBJECT, "deco", "장식", 3, false); deco.fillIconIfEmpty("🪴");
        sprite(deco, "plant", "화분", "🪴", 24, 1);
        sprite(deco, "lamp", "조명", "💡", 25, 2);
        DecorateCategory struct = cat(DecorateGroup.OBJECT, "structure", "구조", 4, false); struct.fillIconIfEmpty("🚧");
        gakbyeok(struct, "wall", "가벽", "#e3d7c4", 46, "🚧", 1);

        // ── 벽배너 색 ──
        DecorateCategory bn = cat(DecorateGroup.BANNER, "basic", "기본", 1, false);
        color(bn, "banner1", "빨강", "#e05a5a", 1);
        color(bn, "banner2", "파랑", "#4a90d9", 2);
        color(bn, "banner3", "초록", "#43b581", 3);
        color(bn, "banner4", "주황", "#f0a030", 4);
        color(bn, "banner5", "보라", "#9b59b6", 5);
        color(bn, "banner6", "검정", "#2c2c40", 6);
        color(bn, "banner7", "분홍", "#ec7fb0", 7);

        // ── 캐릭터(부위별 소분류 = 잠금) ──
        DecorateCategory skin = cat(DecorateGroup.CHARACTER, "SKIN", "피부", 1, true);
        color(skin, "skin1", "밝은살", "#f7d6b6", 1);
        color(skin, "skin2", "살구", "#f0c49b", 2);
        color(skin, "skin3", "황갈", "#e0ac82", 3);
        color(skin, "skin4", "갈색", "#c68642", 4);
        color(skin, "skin5", "진갈", "#8d5524", 5);

        DecorateCategory hairStyle = cat(DecorateGroup.CHARACTER, "HAIRSTYLE", "머리 스타일", 2, true);
        hair(hairStyle, "short", "숏", "{\"type\":\"fringe\"}", 1);
        hair(hairStyle, "bob", "단발", "{\"type\":\"mass\",\"len\":-41}", 2);
        hair(hairStyle, "long", "긴머리", "{\"type\":\"mass\",\"len\":-27}", 3);
        hair(hairStyle, "bald", "민머리", "{\"type\":\"bald\"}", 4);

        DecorateCategory hairColor = cat(DecorateGroup.CHARACTER, "HAIRCOLOR", "머리색", 3, true);
        color(hairColor, "hair1", "검정", "#2b2018", 1);
        color(hairColor, "hair2", "다크브라운", "#5a3a22", 2);
        color(hairColor, "hair3", "브라운", "#96612e", 3);
        color(hairColor, "hair4", "금발", "#d9b45b", 4);
        color(hairColor, "hair5", "실버", "#bdbdbd", 5);
        color(hairColor, "hair6", "핑크", "#e0607a", 6);
        color(hairColor, "hair7", "블루", "#5a6cc0", 7);

        DecorateCategory outfit = cat(DecorateGroup.CHARACTER, "OUTFIT", "옷색", 4, true);
        color(outfit, "outfit1", "빨강", "#e05a5a", 1);
        color(outfit, "outfit2", "파랑", "#4a90d9", 2);
        color(outfit, "outfit3", "초록", "#43b581", 3);
        color(outfit, "outfit4", "주황", "#f0a030", 4);
        color(outfit, "outfit5", "보라", "#9b59b6", 5);
        color(outfit, "outfit6", "차콜", "#39404d", 6);
        color(outfit, "outfit7", "분홍", "#ec7fb0", 7);

        DecorateCategory hat = cat(DecorateGroup.CHARACTER, "HAT", "모자", 5, true);
        hatItem(hat, "none", "없음", "{\"type\":\"preset\",\"preset\":\"none\"}", 1);
        hatItem(hat, "chef", "요리사", "{\"type\":\"preset\",\"preset\":\"chef\"}", 2);
        hatItem(hat, "cap", "캡모자", "{\"type\":\"preset\",\"preset\":\"cap\"}", 3);
        hatItem(hat, "beanie", "비니", "{\"type\":\"preset\",\"preset\":\"beanie\"}", 4);

        log.info("[부트스트랩] 가게 꾸미기 기본 카탈로그를 생성했다.");
    }

    private DecorateCategory cat(DecorateGroup g, String key, String label, int order, boolean locked) {
        return categoryRepository.save(DecorateCategory.create(g, key, label, null, order, locked));
    }

    /** 색 기반 항목(벽지·바닥·벽배너·캐릭터 색). 자유 추가 가능하도록 locked=false. */
    private void color(DecorateCategory c, String key, String label, String hex, int order) {
        itemRepository.save(DecorateItem.create(c, key, label, hex, null, null, null, null, null, order, false));
    }

    /** 이모지 도구(자유 추가 가능). */
    private void sprite(DecorateCategory c, String key, String label, String emoji, int sz, int order) {
        itemRepository.save(DecorateItem.create(c, key, label, null, emoji, "sprite", sz, null, null, order, false));
    }

    /** 벡터로 그리는 도구(테이블·카운터 등). 편집 가능하도록 locked=false. */
    private void vector(DecorateCategory c, String key, String label, String renderKind, String emoji, int sz, int order) {
        itemRepository.save(DecorateItem.create(c, key, label, null, emoji, renderKind, sz, null, null, order, false));
    }

    /** 가벽(얇은 벽 판). 편집 가능하도록 locked=false. */
    private void gakbyeok(DecorateCategory c, String key, String label, String color, int wallH, String emoji, int order) {
        itemRepository.save(DecorateItem.create(c, key, label, color, emoji, "wall", null, wallH, null, order, false));
    }

    /** 캐릭터 머리스타일 — 파라미터(형태/길이) 기반. 디자인 변경 가능하도록 locked=false. */
    private void hair(DecorateCategory c, String key, String label, String renderData, int order) {
        itemRepository.save(DecorateItem.create(c, key, label, null, null, "hairstyle", null, null, renderData, order, false));
    }

    /** 캐릭터 모자 — 프리셋/이미지 기반. 디자인 변경 가능하도록 locked=false. */
    private void hatItem(DecorateCategory c, String key, String label, String renderData, int order) {
        itemRepository.save(DecorateItem.create(c, key, label, null, null, "hat", null, null, renderData, order, false));
    }

    /** 이미 심어진 설치: 머리스타일 항목에 디자인 데이터가 없으면 채우고 잠금 해제(멱등). */
    private void upgradeHairstyles() {
        DecorateCategory hs = categoryRepository.findByGroupAndCategoryKey(DecorateGroup.CHARACTER, "HAIRSTYLE").orElse(null);
        if (hs == null) return;
        java.util.Map<String, String> def = new java.util.HashMap<>();
        def.put("short", "{\"type\":\"fringe\"}");
        def.put("bob", "{\"type\":\"mass\",\"len\":-41}");
        def.put("long", "{\"type\":\"mass\",\"len\":-27}");
        def.put("bald", "{\"type\":\"bald\"}");
        for (DecorateItem it : itemRepository.findByCategory_IdOrderBySortOrderAscIdAsc(hs.getId())) {
            if (it.getRenderData() == null) {
                it.applyDesignAndUnlock(def.getOrDefault(it.getItemKey(), "{\"type\":\"mass\",\"len\":-34}"));
                log.info("[부트스트랩] 머리스타일 디자인 데이터 채움: {}", it.getItemKey());
            }
        }
    }

    /** 이미 심어진 설치: 도구 항목의 잠금을 해제한다(멱등) — 이름·표현 방식 편집 가능하게. */
    private void upgradeUnlockTools() {
        for (DecorateCategory c : categoryRepository.findAllByOrderByGroupAscSortOrderAscIdAsc()) {
            if (c.getGroup() != DecorateGroup.OBJECT) continue;
            for (DecorateItem it : itemRepository.findByCategory_IdOrderBySortOrderAscIdAsc(c.getId())) {
                if (it.isLocked()) it.unlock();
            }
        }
    }

    /** 이미 심어진 설치: 도구 분류 아이콘이 없으면 기본값으로 채운다(멱등). */
    private void upgradeCategoryIcons() {
        java.util.Map<String, String> icons = new java.util.HashMap<>();
        icons.put("furniture", "🪑");
        icons.put("facility", "☕");
        icons.put("deco", "🪴");
        icons.put("structure", "🚧");
        for (DecorateCategory c : categoryRepository.findAllByOrderByGroupAscSortOrderAscIdAsc()) {
            if (c.getGroup() == DecorateGroup.OBJECT && icons.containsKey(c.getCategoryKey())) {
                c.fillIconIfEmpty(icons.get(c.getCategoryKey()));
            }
        }
    }

    /** 이미 심어진 설치: 모자 항목에 디자인 데이터가 없으면 채우고 잠금 해제(멱등). */
    private void upgradeHats() {
        DecorateCategory hc = categoryRepository.findByGroupAndCategoryKey(DecorateGroup.CHARACTER, "HAT").orElse(null);
        if (hc == null) return;
        java.util.Map<String, String> def = new java.util.HashMap<>();
        def.put("none", "{\"type\":\"preset\",\"preset\":\"none\"}");
        def.put("chef", "{\"type\":\"preset\",\"preset\":\"chef\"}");
        def.put("cap", "{\"type\":\"preset\",\"preset\":\"cap\"}");
        def.put("beanie", "{\"type\":\"preset\",\"preset\":\"beanie\"}");
        for (DecorateItem it : itemRepository.findByCategory_IdOrderBySortOrderAscIdAsc(hc.getId())) {
            if (it.getRenderData() == null) {
                it.applyDesignAndUnlock(def.getOrDefault(it.getItemKey(), "{\"type\":\"preset\",\"preset\":\"none\"}"));
                log.info("[부트스트랩] 모자 디자인 데이터 채움: {}", it.getItemKey());
            }
        }
    }
}
