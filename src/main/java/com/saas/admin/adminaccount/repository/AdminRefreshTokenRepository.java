package com.saas.admin.adminaccount.repository;

import com.saas.admin.adminaccount.domain.AdminRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshToken, Long> {

    Optional<AdminRefreshToken> findByTokenHash(String tokenHash);

    /** 로그아웃 / 계정 삭제 시 이 사람의 살아있는 토큰을 전부 찾아 폐기한다. */
    List<AdminRefreshToken> findByEmpNoAndRevokedAtIsNull(String empNo);
}
