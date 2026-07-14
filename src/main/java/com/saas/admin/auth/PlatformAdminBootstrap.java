package com.saas.admin.auth;

import com.saas.admin.auth.domain.UserAccount;
import com.saas.admin.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 플랫폼 관리자 1명을 부트스트랩한다.
 * <p>
 * 비밀번호 해시를 마이그레이션 SQL 에 하드코딩하지 않으려고 환경변수로 주입받는다.
 * 이미 플랫폼 관리자가 있으면 아무것도 하지 않는다 — 재기동 시 덮어쓰거나 중복 생성하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrap implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.admin.bootstrap-enabled:false}")
    private boolean enabled;

    @Value("${platform.admin.email:}")
    private String email;

    @Value("${platform.admin.password:}")
    private String password;

    @Value("${platform.admin.name:플랫폼 관리자}")
    private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("부트스트랩이 켜져 있으나 SAAS_ADMIN_EMAIL / SAAS_ADMIN_PASSWORD 가 비어 있어 건너뛴다.");
            return;
        }
        if (userAccountRepository.existsByPlatformAdminTrue()) {
            log.info("플랫폼 관리자가 이미 존재한다. 부트스트랩을 건너뛴다.");
            return;
        }
        if (userAccountRepository.existsByEmail(email)) {
            log.warn("이미 존재하는 이메일이라 부트스트랩을 건너뛴다: {}", email);
            return;
        }

        UserAccount admin = UserAccount.createPlatformAdmin(
                email, passwordEncoder.encode(password), name);
        userAccountRepository.save(admin);

        log.info("플랫폼 관리자 생성됨: {} (user_id={})", email, admin.getId());
        log.warn("보안: 부트스트랩이 끝났으면 SAAS_ADMIN_BOOTSTRAP=false 로 되돌리고 "
                + "SAAS_ADMIN_PASSWORD 를 .env 에서 제거할 것.");
    }
}
