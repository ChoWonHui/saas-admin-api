package com.saas.admin.code;

import com.saas.admin.code.domain.CodeGroup;
import com.saas.admin.code.domain.CommonCode;
import com.saas.admin.code.repository.CodeGroupRepository;
import com.saas.admin.code.repository.CommonCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기본 공통코드를 만든다. 관리자 화면의 부서/직급/직책 선택지가 첫 기동부터 비어 있지 않게 한다.
 * <p>
 * <b>비어 있을 때만</b> 심는다 — 관리자가 지우거나 바꾼 코드를 재기동이 되살리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeBootstrap implements ApplicationRunner {

    private final CodeGroupRepository groupRepository;
    private final CommonCodeRepository codeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (groupRepository.count() > 0) {
            return;   // 이미 코드가 있다. 건드리지 않는다.
        }

        // 부서코드의 진실 공급원. 조직(조직도)의 부서코드는 여기 등록된 것만 쓸 수 있다.
        seed("DEPARTMENT", "부서", "조직도의 부서코드가 여기서 온다",
                List.of("HQ:본사", "PF:플랫폼본부", "MGMT:경영지원본부", "DEV:플랫폼개발팀",
                        "INFRA:인프라팀", "HR:인사팀", "CS:고객지원팀"));
        seed("JOB_GRADE", "직급", "사원 → 부장 순",
                List.of("STAFF:사원", "SENIOR:대리", "MANAGER:과장", "DEPUTY:차장", "GENERAL:부장"));
        seed("JOB_TITLE", "직책", "보직 — 없을 수 있다",
                List.of("PART_LEAD:파트장", "TEAM_LEAD:팀장", "DIRECTOR:실장"));

        log.info("[부트스트랩] 기본 공통코드를 생성했다. (부서/직급/직책 3그룹)");
    }

    private void seed(String groupCode, String name, String description, List<String> codes) {
        CodeGroup group = groupRepository.save(CodeGroup.create(groupCode, name, description));
        int order = 1;
        for (String entry : codes) {
            String[] parts = entry.split(":", 2);
            codeRepository.save(CommonCode.create(group, parts[0], parts[1], order++));
        }
    }
}
