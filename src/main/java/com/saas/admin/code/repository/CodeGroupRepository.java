package com.saas.admin.code.repository;

import com.saas.admin.code.domain.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, String> {

    List<CodeGroup> findAllByOrderByGroupCodeAsc();
}
