package com.saas.admin.auth.repository;

import com.saas.admin.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPlatformAdminTrue();

    /**
     * 업체(테넌트) 안에서 로그인 아이디로 계정을 찾는다.
     * login_id 는 전역이 아니라 '가게 안에서만' 유일하므로, 반드시 테넌트로 한정해서 찾는다.
     */
    @Query("""
            select u from UserAccount u
            where u.loginId = :loginId
              and u.id in (select tu.userId from TenantUser tu where tu.tenantId = :tenantId)
            """)
    Optional<UserAccount> findByTenantIdAndLoginId(@Param("tenantId") Long tenantId,
                                                   @Param("loginId") String loginId);

    /**
     * 업체 안에서 이메일로 계정을 찾는다.
     * 로그인 칸에 아이디 대신 이메일을 넣는 사람을 위해 아이디 조회가 실패하면 이걸로 보조 조회한다.
     */
    @Query("""
            select u from UserAccount u
            where u.email = :email
              and u.id in (select tu.userId from TenantUser tu where tu.tenantId = :tenantId)
            """)
    Optional<UserAccount> findByTenantIdAndEmail(@Param("tenantId") Long tenantId,
                                                 @Param("email") String email);
}
