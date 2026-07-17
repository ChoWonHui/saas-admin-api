package com.saas.admin.org;

import com.saas.admin.org.domain.Organization;
import com.saas.admin.org.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기본 조직 트리를 만든다. 관리자 화면 조직도가 첫 기동부터 비어 있지 않게 한다.
 * <b>비어 있을 때만</b> 심는다 — 관리자가 바꾼 조직을 재기동이 되살리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgBootstrap implements ApplicationRunner {

    private final OrganizationRepository orgRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orgRepository.count() > 0) {
            return;
        }
        // 회사 → 본부 → 팀 예시 (여러 단 트리). 각 조직에 부서코드를 부여한다.
        Organization company = orgRepository.save(Organization.create(null, "HQ", "EXPRISM", 1));
        Organization platformHq = orgRepository.save(Organization.create(company, "PF", "플랫폼본부", 1));
        Organization bizHq = orgRepository.save(Organization.create(company, "MGMT", "경영지원본부", 2));
        orgRepository.save(Organization.create(platformHq, "DEV", "플랫폼개발팀", 1));
        orgRepository.save(Organization.create(platformHq, "INFRA", "인프라팀", 2));
        orgRepository.save(Organization.create(bizHq, "HR", "인사팀", 1));

        log.info("[부트스트랩] 기본 조직 6개를 생성했다. (EXPRISM > 본부 2 > 팀 3)");
    }
}
