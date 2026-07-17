package com.saas.admin.permission;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.menu.domain.AdminMenu;
import com.saas.admin.menu.repository.AdminMenuRepository;
import com.saas.admin.permission.domain.MenuPermission;
import com.saas.admin.permission.domain.PermissionSubject;
import com.saas.admin.permission.repository.MenuPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 메뉴 권한. 부서(조직)별·직책별로 허용 메뉴를 관리하고, 사원 한 명의 최종 접근 메뉴를 계산한다.
 * <p>
 * 규칙(빈틈 방지):
 * <ul>
 *   <li><b>Default Deny</b> — 허용 목록에 없는 메뉴는 접근 불가.</li>
 *   <li><b>합집합</b> — 최종 = 부서 허용 ∪ 직책 허용.</li>
 *   <li><b>부모 자동 포함</b> — 하위 메뉴가 허용되면 그 부모(경로)도 보이게 한다.</li>
 *   <li><b>대시보드 항상 허용</b> — 어디서 튕겨도 갈 곳이 있어야 한다.</li>
 *   <li><b>슈퍼관리자 전권</b> — 권한 규칙을 우회한다(자물쇠 사고 방지).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 권한과 무관하게 항상 접근 가능한 메뉴 경로. */
    private static final String DASHBOARD_URL = "/dashboard";

    private final MenuPermissionRepository permissionRepository;
    private final AdminMenuRepository menuRepository;

    /** 부서·직책 키 조합 규칙: 직책 키는 "조직id:직책명". */
    public static String deptTitleKey(Long orgId, String jobTitle) {
        return orgId + ":" + jobTitle;
    }

    /**
     * 이 사원이 특정 URL 의 메뉴에 접근할 수 있는가.
     * <p>권한관리 화면(/permissions)처럼, 화면 자체의 접근 통제를 메뉴 권한으로 판정할 때 쓴다.
     * 슈퍼는 {@link #allowedMenuIds}가 전 메뉴를 주므로 항상 true.
     */
    @Transactional(readOnly = true)
    public boolean hasMenuAccess(AdminAccount admin, String menuUrl) {
        Set<Long> allowed = allowedMenuIds(admin);
        return menuRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(m -> menuUrl.equals(m.getUrl()))
                .anyMatch(m -> allowed.contains(m.getId()));
    }

    /** 한 주체(부서 or 부서+직책)에 허용된 메뉴 id 목록. 권한관리 화면이 체크 상태를 그린다. */
    @Transactional(readOnly = true)
    public List<Long> permissionsOf(PermissionSubject type, String key) {
        return permissionRepository.findBySubjectTypeAndSubjectKey(type, key).stream()
                .map(MenuPermission::getMenuId)
                .toList();
    }

    /**
     * 한 주체의 권한을 보낸 목록으로 통째 교체한다.
     * <p>
     * 직책(DEPT_TITLE) 저장 시에는 <b>부서 허용 메뉴의 부분집합</b>인지 검증한다 —
     * 부서가 안 준 메뉴를 직책에 줄 수 없다(빈틈 방지, 서버 강제).
     */
    @Transactional
    public void replacePermissions(PermissionSubject type, String key, List<Long> menuIds) {
        List<Long> ids = menuIds == null ? List.of() : menuIds.stream().distinct().toList();

        if (type == PermissionSubject.DEPT_TITLE) {
            String orgId = key.substring(0, key.indexOf(':'));
            Set<Long> deptAllowed = permissionRepository
                    .findBySubjectTypeAndSubjectKey(PermissionSubject.DEPT, orgId).stream()
                    .map(MenuPermission::getMenuId).collect(Collectors.toSet());
            if (!deptAllowed.containsAll(ids)) {
                throw new ApiException(ErrorCode.PERM_NOT_SUBSET);
            }
        }

        permissionRepository.deleteBySubjectTypeAndSubjectKey(type, key);
        ids.stream().map(id -> MenuPermission.of(type, key, id)).forEach(permissionRepository::save);
    }

    /**
     * 이 사원이 접근할 수 있는 메뉴 id 집합. 로그인 후 프론트가 받아 네비게이션·라우트 가드에 쓴다.
     * 서버도 같은 계산으로 접근을 강제한다.
     */
    @Transactional(readOnly = true)
    public Set<Long> allowedMenuIds(AdminAccount admin) {
        List<AdminMenu> menus = menuRepository.findAllByOrderBySortOrderAscIdAsc();

        // 슈퍼는 전 메뉴.
        if (admin.isSuper()) {
            Set<Long> all = new HashSet<>();
            menus.forEach(m -> all.add(m.getId()));
            return all;
        }

        Set<Long> allowed = new HashSet<>();

        // 부서가 상한선. 자기 부서·직책에 설정이 있으면 그것(더 좁은 것), 없으면 부서 기본 전체.
        if (admin.getOrgId() != null) {
            boolean titleSet = false;
            if (admin.getJobTitle() != null && !admin.getJobTitle().isBlank()) {
                String titleKey = deptTitleKey(admin.getOrgId(), admin.getJobTitle());
                List<MenuPermission> titlePerms =
                        permissionRepository.findBySubjectTypeAndSubjectKey(PermissionSubject.DEPT_TITLE, titleKey);
                if (!titlePerms.isEmpty()) {
                    titlePerms.forEach(p -> allowed.add(p.getMenuId()));
                    titleSet = true;
                }
            }
            if (!titleSet) {
                permissionRepository.findBySubjectTypeAndSubjectKey(PermissionSubject.DEPT, String.valueOf(admin.getOrgId()))
                        .forEach(p -> allowed.add(p.getMenuId()));
            }
        }

        // 대시보드는 항상 포함
        menus.stream().filter(m -> DASHBOARD_URL.equals(m.getUrl())).forEach(m -> allowed.add(m.getId()));

        // 허용된 메뉴의 부모들을 함께 포함 — 경로가 끊기지 않게
        Map<Long, Long> parentOf = new HashMap<>();
        for (AdminMenu m : menus) {
            parentOf.put(m.getId(), m.parentId());
        }
        for (Long id : new HashSet<>(allowed)) {
            Long cursor = parentOf.get(id);
            while (cursor != null && allowed.add(cursor)) {
                cursor = parentOf.get(cursor);
            }
        }
        return allowed;
    }
}
