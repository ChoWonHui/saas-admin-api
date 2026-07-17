package com.saas.admin.org;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.code.repository.CommonCodeRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.org.domain.Organization;
import com.saas.admin.org.dto.OrgDtos.*;
import com.saas.admin.org.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 조직과 조직도.
 * <p>
 * 조직은 여러 단 트리다(깊이 제한 없음). 순환만 막는다. 부서장은 그 조직 소속 사원이어야 한다.
 * 사원 배치·부서장은 조직이 관리한다 — "누가 어느 팀 소속이고 누가 팀장인가"의 진실 공급원이 여기다.
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    private static final String NOT_DELETED = "N";
    /** 부서코드의 진실 공급원. 조직의 부서코드는 이 공통코드 그룹에 등록된 것만 쓸 수 있다. */
    private static final String DEPT_GROUP = "DEPARTMENT";

    private final OrganizationRepository orgRepository;
    private final AdminAccountRepository adminRepository;
    private final CommonCodeRepository commonCodeRepository;

    // ---- 조직도 (좌: 사원, 우: 트리) ----

    /** 조직 트리 + 각 조직의 소속 사원(부서장 먼저). 화면 우측 조직도용. */
    @Transactional(readOnly = true)
    public List<OrgNode> orgChart() {
        List<Organization> orgs = orgRepository.findAllByOrderBySortOrderAscIdAsc();
        // 조직별 소속 사원 모으기
        Map<Long, List<AdminAccount>> byOrg = adminRepository.findByDeletedOrderByEmpNoAsc(NOT_DELETED).stream()
                .filter(a -> a.getOrgId() != null)
                .collect(Collectors.groupingBy(AdminAccount::getOrgId));

        Map<Long, List<Organization>> childrenOf = orgs.stream()
                .filter(o -> !o.isTopLevel())
                .collect(Collectors.groupingBy(Organization::parentId));

        return orgs.stream()
                .filter(Organization::isTopLevel)
                .map(top -> toNode(top, childrenOf, byOrg))
                .toList();
    }

    private OrgNode toNode(Organization org, Map<Long, List<Organization>> childrenOf,
                           Map<Long, List<AdminAccount>> byOrg) {
        List<MemberNode> members = byOrg.getOrDefault(org.getId(), List.of()).stream()
                .map(a -> new MemberNode(a.getEmpNo(), a.getName(), a.getJobGrade(), a.getJobTitle(),
                        org.isLeader(a.getEmpNo())))
                // 부서장을 맨 위로, 그 다음 사번 순
                .sorted(Comparator.comparing(MemberNode::leader).reversed().thenComparing(MemberNode::empNo))
                .toList();
        List<OrgNode> children = childrenOf.getOrDefault(org.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenOf, byOrg))
                .toList();
        return new OrgNode(org.getId(), org.parentId(), org.getOrgCode(), org.getName(),
                org.getSortOrder(), members, children);
    }

    // ---- 조직 CRUD ----

    @Transactional
    public OrgResponse create(CreateOrgRequest request) {
        String code = request.orgCode().trim().toUpperCase();
        requireRegisteredDeptCode(code);
        if (orgRepository.existsByOrgCode(code)) {
            throw new ApiException(ErrorCode.ORG_CODE_DUPLICATED);
        }
        Organization parent = resolveParent(request.parentId());
        List<Organization> all = orgRepository.findAllByOrderBySortOrderAscIdAsc();
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : nextSortOrder(all, request.parentId());
        return OrgResponse.from(orgRepository.save(Organization.create(parent, code, request.name(), sortOrder)));
    }

    @Transactional
    public OrgResponse update(Long id, UpdateOrgRequest request) {
        Organization org = get(id);
        String code = request.orgCode().trim().toUpperCase();
        requireRegisteredDeptCode(code);
        if (orgRepository.existsByOrgCodeAndIdNot(code, id)) {
            throw new ApiException(ErrorCode.ORG_CODE_DUPLICATED);
        }
        org.update(code, request.name());
        return OrgResponse.from(org);
    }

    /** 부서코드는 공통코드 DEPARTMENT 그룹에 등록된 것만 허용한다. 자유 입력을 막는다. */
    private void requireRegisteredDeptCode(String code) {
        if (!commonCodeRepository.existsByGroupGroupCodeAndCode(DEPT_GROUP, code)) {
            throw new ApiException(ErrorCode.ORG_CODE_NOT_REGISTERED);
        }
    }

    @Transactional
    public void delete(Long id) {
        Organization org = get(id);
        if (orgRepository.existsByParentId(id)) {
            throw new ApiException(ErrorCode.ORG_HAS_CHILDREN);
        }
        if (adminRepository.countByOrgIdAndDeleted(id, NOT_DELETED) > 0) {
            throw new ApiException(ErrorCode.ORG_HAS_MEMBERS);
        }
        orgRepository.delete(org);
    }

    /** 드래그앤드랍 이동 — 상위/순서만 바꾸고, 순환을 막는다. */
    @Transactional
    public void move(Long id, MoveOrgRequest request) {
        Organization org = get(id);
        Organization newParent = resolveParent(request.parentId());
        // 자기 자신이나 자기 후손을 상위로 지정하면 트리가 끊긴다
        if (request.parentId() != null && isSelfOrDescendant(id, request.parentId())) {
            throw new ApiException(ErrorCode.ORG_CYCLE);
        }

        List<Organization> all = orgRepository.findAllByOrderBySortOrderAscIdAsc();
        Long oldParentId = org.parentId();

        List<Organization> newSiblings = new ArrayList<>(all.stream()
                .filter(o -> Objects.equals(o.parentId(), request.parentId()))
                .filter(o -> !o.getId().equals(id))
                .toList());
        int position = Math.min(request.position(), newSiblings.size());
        newSiblings.add(position, org);

        org.moveTo(newParent);
        for (int i = 0; i < newSiblings.size(); i++) {
            newSiblings.get(i).changeSortOrder(i + 1);
        }
        if (!Objects.equals(oldParentId, request.parentId())) {
            List<Organization> oldSiblings = all.stream()
                    .filter(o -> Objects.equals(o.parentId(), oldParentId))
                    .filter(o -> !o.getId().equals(id))
                    .toList();
            for (int i = 0; i < oldSiblings.size(); i++) {
                oldSiblings.get(i).changeSortOrder(i + 1);
            }
        }
    }

    // ---- 부서장 / 사원 배치 ----

    /** 부서장 지정/해제. 빈 값이면 해제. 지정할 땐 그 조직 소속이어야 한다. */
    @Transactional
    public void assignLeader(Long id, AssignLeaderRequest request) {
        Organization org = get(id);
        String empNo = (request.empNo() == null || request.empNo().isBlank()) ? null : request.empNo();
        if (empNo != null) {
            AdminAccount admin = adminRepository.findByEmpNoAndDeleted(empNo, NOT_DELETED)
                    .orElseThrow(() -> new ApiException(ErrorCode.ADMIN_NOT_FOUND));
            if (!Objects.equals(admin.getOrgId(), id)) {
                throw new ApiException(ErrorCode.LEADER_NOT_IN_ORG);
            }
        }
        org.assignLeader(empNo);
    }

    /** 관리자를 조직에 배치(드래그앤드랍). orgId 가 null 이면 미배치로. */
    @Transactional
    public void assignMember(String empNo, AssignMemberRequest request) {
        AdminAccount admin = adminRepository.findByEmpNoAndDeleted(empNo, NOT_DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.ADMIN_NOT_FOUND));
        Long oldOrgId = admin.getOrgId();
        Long newOrgId = request.orgId();

        Organization newOrg = newOrgId != null ? get(newOrgId) : null; // 존재 확인
        admin.assignOrg(newOrgId);
        // 부서(상세보기) 라벨을 배치된 조직에 맞춰 동기화한다. 미배치면 비운다.
        // → 조직도에서 옮기면 관리자 상세의 '부서'도 즉시 그 조직으로 바뀐다.
        admin.syncDepartment(newOrg != null ? departmentNameOf(newOrg) : null);

        // 옮겨간 사람이 이전 조직의 부서장이었다면 그 부서장 자리를 비운다 (남의 조직 팀장일 수 없다)
        if (oldOrgId != null && !Objects.equals(oldOrgId, newOrgId)) {
            orgRepository.findById(oldOrgId)
                    .filter(o -> o.isLeader(empNo))
                    .ifPresent(o -> o.assignLeader(null));
        }
    }

    /** 조직의 부서코드(org_code)에 해당하는 DEPARTMENT 공통코드 이름. 없으면 조직 이름으로 대체. */
    private String departmentNameOf(Organization org) {
        if (org.getOrgCode() == null) return org.getName();
        return commonCodeRepository.findByGroupGroupCodeAndCode(DEPT_GROUP, org.getOrgCode())
                .map(com.saas.admin.code.domain.CommonCode::getName)
                .orElse(org.getName());
    }

    // ---- 내부 ----

    private Organization get(Long id) {
        return orgRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.ORG_NOT_FOUND));
    }

    private Organization resolveParent(Long parentId) {
        if (parentId == null) return null;
        return orgRepository.findById(parentId).orElseThrow(() -> new ApiException(ErrorCode.ORG_PARENT_NOT_FOUND));
    }

    /** targetId 가 movingId 자신이거나 그 후손이면 true (순환 방지). */
    private boolean isSelfOrDescendant(Long movingId, Long targetId) {
        Map<Long, Long> parentOf = new HashMap<>();
        for (Organization o : orgRepository.findAllByOrderBySortOrderAscIdAsc()) {
            parentOf.put(o.getId(), o.parentId());
        }
        Long cursor = targetId;
        while (cursor != null) {
            if (cursor.equals(movingId)) return true;
            cursor = parentOf.get(cursor);
        }
        return false;
    }

    private int nextSortOrder(List<Organization> all, Long parentId) {
        return all.stream()
                .filter(o -> Objects.equals(o.parentId(), parentId))
                .mapToInt(Organization::getSortOrder)
                .max().orElse(0) + 1;
    }
}
