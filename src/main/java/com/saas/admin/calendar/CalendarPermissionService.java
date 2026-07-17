package com.saas.admin.calendar;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.calendar.domain.CalendarPermission;
import com.saas.admin.calendar.repository.CalendarPermissionRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 달력 권한. 범위(전체/팀/개인) × 액션(보기/작성)의 6개 권한키를 부서·직책별로 관리한다.
 * 규칙은 메뉴 권한과 같다: Default Deny, 부서 상한, 직책이 그 안에서 좁힘, 슈퍼 전권.
 */
@Service
@RequiredArgsConstructor
public class CalendarPermissionService {

    private static final String DEPT = "DEPT";
    private static final String DEPT_TITLE = "DEPT_TITLE";
    public static final List<String> ALL_KEYS = List.of(
            "ALL_VIEW", "ALL_WRITE", "TEAM_VIEW", "TEAM_WRITE", "PERSONAL_VIEW", "PERSONAL_WRITE");

    private final CalendarPermissionRepository permissionRepository;

    public static String deptTitleKey(Long orgId, String jobTitle) {
        return orgId + ":" + jobTitle;
    }

    @Transactional(readOnly = true)
    public List<String> permissionsOf(String type, String key) {
        return permissionRepository.findBySubjectTypeAndSubjectKey(type, key).stream()
                .map(CalendarPermission::getPermKey).toList();
    }

    @Transactional
    public void replacePermissions(String type, String key, List<String> permKeys) {
        List<String> keys = permKeys == null ? List.of()
                : permKeys.stream().filter(ALL_KEYS::contains).distinct().toList();

        // 직책은 부서가 허용한 범위의 부분집합이어야 한다.
        if (DEPT_TITLE.equals(type)) {
            String orgId = key.substring(0, key.indexOf(':'));
            Set<String> deptAllowed = permissionRepository.findBySubjectTypeAndSubjectKey(DEPT, orgId).stream()
                    .map(CalendarPermission::getPermKey).collect(Collectors.toSet());
            if (!deptAllowed.containsAll(keys)) {
                throw new ApiException(ErrorCode.CAL_PERM_NOT_SUBSET);
            }
        }

        permissionRepository.deleteBySubjectTypeAndSubjectKey(type, key);
        keys.stream().map(k -> CalendarPermission.of(type, key, k)).forEach(permissionRepository::save);
    }

    /** 이 사원의 달력 권한 집합. 슈퍼는 전부. 부서·직책에 설정 있으면 그것(좁은 것), 없으면 부서 기본. */
    @Transactional(readOnly = true)
    public Set<String> myPermissions(AdminAccount admin) {
        if (admin.isSuper()) {
            return Set.copyOf(ALL_KEYS);
        }
        if (admin.getOrgId() == null) {
            return Set.of();
        }
        if (admin.getJobTitle() != null && !admin.getJobTitle().isBlank()) {
            List<CalendarPermission> titlePerms = permissionRepository.findBySubjectTypeAndSubjectKey(
                    DEPT_TITLE, deptTitleKey(admin.getOrgId(), admin.getJobTitle()));
            if (!titlePerms.isEmpty()) {
                return titlePerms.stream().map(CalendarPermission::getPermKey).collect(Collectors.toSet());
            }
        }
        return permissionRepository.findBySubjectTypeAndSubjectKey(DEPT, String.valueOf(admin.getOrgId())).stream()
                .map(CalendarPermission::getPermKey).collect(Collectors.toSet());
    }
}
