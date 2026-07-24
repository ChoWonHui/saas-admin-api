package com.saas.admin.tenant.staff;

import com.saas.admin.auth.domain.Role;
import com.saas.admin.auth.domain.TenantUser;
import com.saas.admin.auth.domain.TenantUserStatus;
import com.saas.admin.auth.domain.UserAccount;
import com.saas.admin.auth.repository.RoleRepository;
import com.saas.admin.auth.repository.TenantUserRepository;
import com.saas.admin.auth.repository.UserAccountRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.repository.TenantRepository;
import com.saas.admin.tenant.staff.StaffDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 업체 직원(로그인 계정) 관리 — 대표/매니저/직원 계정을 만들고 관리한다. */
@Service
@RequiredArgsConstructor
public class TenantStaffService {

    /** 직원 관리 대상 역할: 대표(2)·매니저(3)·직원(4). 고객(5)은 제외. */
    private static final List<Integer> STAFF_ROLES = List.of(2, 3, 4);

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StaffResponse> list(Long tenantId) {
        requireTenant(tenantId);
        List<TenantUser> members = tenantUserRepository.findByTenantIdAndRoleIdIn(tenantId, STAFF_ROLES);
        Map<Integer, String> roleNames = roleNames();
        Map<Long, UserAccount> users = userAccountRepository
                .findAllById(members.stream().map(TenantUser::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
        return members.stream()
                .sorted((a, b) -> Integer.compare(a.getRoleId(), b.getRoleId())) // 대표 → 직원 순
                .map((m) -> toResponse(m, users.get(m.getUserId()), roleNames))
                .toList();
    }

    @Transactional
    public StaffResponse create(Long tenantId, StaffCreateRequest req) {
        Tenant tenant = requireTenant(tenantId);
        if (!STAFF_ROLES.contains(req.roleId())) {
            throw new ApiException(ErrorCode.STAFF_ROLE_INVALID);
        }
        String loginId = req.loginId().trim();
        // 아이디는 '가게 안에서만' 유일하면 된다.
        if (userAccountRepository.findByTenantIdAndLoginId(tenantId, loginId).isPresent()) {
            throw new ApiException(ErrorCode.LOGIN_ID_DUPLICATED);
        }
        // 이메일은 선택. 안 넣으면 로그인엔 안 쓰이는 내부용 합성 이메일을 만든다(컬럼이 NOT NULL·유니크라서).
        String email = (req.email() == null || req.email().isBlank())
                ? loginId + "@" + tenant.getCode().toLowerCase() + ".tenant.local"
                : req.email().trim();
        if (userAccountRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (Role.TENANT_OWNER_ID.equals(req.roleId())
                && tenantUserRepository.existsByTenantIdAndRoleId(tenantId, Role.TENANT_OWNER_ID)) {
            throw new ApiException(ErrorCode.TENANT_OWNER_EXISTS);
        }
        UserAccount user = userAccountRepository.save(UserAccount.createTenantUser(
                email, loginId, passwordEncoder.encode(req.password()), req.name(), req.phone()));
        TenantUser member;
        try {
            member = tenantUserRepository.save(TenantUser.of(tenantId, user.getId(), req.roleId()));
        } catch (DataIntegrityViolationException e) {
            // owner_marker 유니크(업체당 대표 1명) 위반
            throw new ApiException(ErrorCode.TENANT_OWNER_EXISTS);
        }
        return toResponse(member, user, roleNames());
    }

    @Transactional
    public StaffResponse update(Long tenantId, Long tenantUserId, StaffUpdateRequest req) {
        TenantUser member = member(tenantId, tenantUserId);
        if (!STAFF_ROLES.contains(req.roleId())) {
            throw new ApiException(ErrorCode.STAFF_ROLE_INVALID);
        }
        // 대표로 승격하려는데 이미 다른 대표가 있으면 막는다.
        if (Role.TENANT_OWNER_ID.equals(req.roleId()) && !member.isOwner()
                && tenantUserRepository.existsByTenantIdAndRoleId(tenantId, Role.TENANT_OWNER_ID)) {
            throw new ApiException(ErrorCode.TENANT_OWNER_EXISTS);
        }
        UserAccount user = userAccountRepository.findById(member.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.STAFF_NOT_FOUND));
        user.updateProfile(req.name(), req.phone());
        try {
            member.changeRole(req.roleId());
            member.setStatus(parseStatus(req.status()));
            tenantUserRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.TENANT_OWNER_EXISTS);
        }
        return toResponse(member, user, roleNames());
    }

    @Transactional
    public void resetPassword(Long tenantId, Long tenantUserId, ResetPasswordRequest req) {
        TenantUser member = member(tenantId, tenantUserId);
        UserAccount user = userAccountRepository.findById(member.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.STAFF_NOT_FOUND));
        user.changePassword(passwordEncoder.encode(req.newPassword()));
    }

    @Transactional
    public void delete(Long tenantId, Long tenantUserId) {
        TenantUser member = member(tenantId, tenantUserId);
        if (member.isOwner()) {
            throw new ApiException(ErrorCode.CANNOT_DELETE_OWNER);
        }
        Long userId = member.getUserId();
        tenantUserRepository.delete(member);
        tenantUserRepository.flush();
        // 이 사용자가 다른 업체에도 소속돼 있지 않으면 로그인 계정 자체도 지운다.
        if (tenantUserRepository.countByUserId(userId) == 0) {
            userAccountRepository.deleteById(userId);
        }
    }

    // ---- 내부 ----

    private Tenant requireTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
    }

    private TenantUser member(Long tenantId, Long tenantUserId) {
        TenantUser m = tenantUserRepository.findById(tenantUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.STAFF_NOT_FOUND));
        if (!m.getTenantId().equals(tenantId)) {
            throw new ApiException(ErrorCode.STAFF_NOT_FOUND);
        }
        return m;
    }

    private Map<Integer, String> roleNames() {
        return roleRepository.findAll().stream().collect(Collectors.toMap(Role::getId, Role::getName));
    }

    private TenantUserStatus parseStatus(String s) {
        if (s == null) return TenantUserStatus.ACTIVE;
        try {
            return TenantUserStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return TenantUserStatus.ACTIVE;
        }
    }

    private StaffResponse toResponse(TenantUser m, UserAccount u, Map<Integer, String> roleNames) {
        return new StaffResponse(
                m.getId(), m.getUserId(),
                u != null ? u.getLoginId() : null,
                u != null ? u.getEmail() : null,
                u != null ? u.getName() : null,
                u != null ? u.getPhone() : null,
                m.getRoleId(), roleNames.getOrDefault(m.getRoleId(), String.valueOf(m.getRoleId())),
                m.getStatus().name(),
                u != null ? u.getLastLoginAt() : null);
    }
}
