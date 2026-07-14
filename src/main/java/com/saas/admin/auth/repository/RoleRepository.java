package com.saas.admin.auth.repository;

import com.saas.admin.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByCode(String code);
}
