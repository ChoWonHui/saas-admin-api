package com.saas.admin.adminaccount;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 관리자 1명을 만든다.
 * <p>
 * <b>왜 필요한가:</b> 관리자를 만들려면 관리자 토큰이 있어야 하는데, 관리자가 하나도 없으면
 * 토큰을 받을 방법이 없다. 이 닭과 달걀을 끊는 것이 이 클래스의 유일한 목적이다.
 * <p>
 * admin_account 가 <b>비어 있을 때만</b> 동작한다. 한 명이라도 있으면 아무 일도 하지 않으므로
 * 재기동해도 덮어쓰지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountBootstrap implements ApplicationRunner {

    private final AdminAccountRepository adminRepository;
    private final EmployeeNoService employeeNoService;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${admin.bootstrap.name:플랫폼 관리자}")
    private String name;

    @Value("${admin.bootstrap.email:}")
    private String email;

    @Value("${admin.bootstrap.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (adminRepository.count() > 0) {
            return;   // 이미 관리자가 있다. 건드리지 않는다.
        }
        if (password == null || password.isBlank()) {
            log.warn("[부트스트랩] admin.bootstrap.password 가 비어 있어 최초 관리자를 만들지 못했다.");
            return;
        }

        String empNo = employeeNoService.issue();
        // 최초 관리자는 설정에 적힌 비밀번호를 그대로 쓴다. (이 계정마저 강제 변경이면 첫 진입이 막힌다)
        AdminAccount first = AdminAccount.create(
                empNo,
                passwordEncoder.encode(password),
                name,
                email.isBlank() ? null : email,
                null,
                null, null, null,   // 부서/직급/직책은 만든 뒤 화면에서 채운다
                false);
        // 최초 관리자는 슈퍼관리자다 — 메뉴 권한 규칙에 갇히지 않는 유일한 계정이다.
        first.grantSuper();
        adminRepository.save(first);

        log.info("[부트스트랩] 최초 슈퍼관리자를 생성했다. 사번={} 이름={}", empNo, name);
    }
}
