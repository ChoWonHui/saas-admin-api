package com.saas.admin.menu;

import com.saas.admin.menu.domain.AdminMenu;
import com.saas.admin.menu.repository.AdminMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기본 메뉴를 만든다.
 * <p>
 * 메뉴가 데이터로 관리되면서 생기는 닭과 달걀: 메뉴 관리 화면으로 가는 메뉴조차 데이터인데,
 * 테이블이 비어 있으면 콘솔 상단에 아무것도 없다. 그래서 <b>비어 있을 때만</b> 기본 메뉴를 심는다.
 * 한 건이라도 있으면 아무 일도 하지 않으므로, 관리자가 지우거나 바꾼 메뉴를 재기동이 되살리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuBootstrap implements ApplicationRunner {

    private final AdminMenuRepository menuRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (menuRepository.count() > 0) {
            return;   // 이미 메뉴가 있다. 건드리지 않는다.
        }

        menuRepository.save(AdminMenu.create(null, "대시보드", "/dashboard", 1));
        menuRepository.save(AdminMenu.create(null, "관리자", "/admins", 2));
        menuRepository.save(AdminMenu.create(null, "업체", "/tenants", 3));
        // "설정" 은 URL 없는 묶음 메뉴 — 드롭다운으로 하위를 펼친다.
        AdminMenu settings = menuRepository.save(AdminMenu.create(null, "설정", null, 4));
        menuRepository.save(AdminMenu.create(settings, "메뉴 관리", "/menus", 1));

        log.info("[부트스트랩] 기본 메뉴 5건을 생성했다. (대시보드/관리자/업체/설정>메뉴 관리)");
    }
}
