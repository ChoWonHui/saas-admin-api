package com.saas.admin.auth.repository;

import com.saas.admin.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 로그아웃 시 해당 사용자의 살아있는 토큰을 모두 끊는다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
