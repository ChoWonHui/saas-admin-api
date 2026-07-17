package com.saas.admin.code;

import com.saas.admin.code.domain.CodeGroup;
import com.saas.admin.code.domain.CommonCode;
import com.saas.admin.code.dto.CodeDtos.*;
import com.saas.admin.code.repository.CodeGroupRepository;
import com.saas.admin.code.repository.CommonCodeRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 공통코드 관리.
 * <ul>
 *   <li>그룹코드·코드값은 <b>불변</b> — 다른 데이터가 이 값을 저장하므로, 이름(라벨)만 바꾼다</li>
 *   <li>그룹에 코드가 남아 있으면 그룹을 지울 수 없다 — 실수로 묶음째 날리는 것을 막는다 (메뉴와 같은 규칙)</li>
 *   <li>그만 쓸 코드는 삭제 대신 use_yn='N' 을 권장 — 과거 데이터의 표시가 유지된다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CodeService {

    private final CodeGroupRepository groupRepository;
    private final CommonCodeRepository codeRepository;

    /** 관리 화면·선택지 공용. 그룹 목록에 codes 가 정렬돼 담겨 나간다. */
    @Transactional(readOnly = true)
    public List<GroupResponse> groups() {
        List<CommonCode> allCodes = codeRepository.findAllByOrderBySortOrderAscIdAsc();
        return groupRepository.findAllByOrderByGroupCodeAsc().stream()
                .map(group -> GroupResponse.of(group, allCodes.stream()
                        .filter(code -> Objects.equals(code.getGroup().getGroupCode(), group.getGroupCode()))
                        .map(CodeResponse::from)
                        .toList()))
                .toList();
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        if (groupRepository.existsById(request.groupCode())) {
            throw new ApiException(ErrorCode.CODE_GROUP_DUPLICATED);
        }
        CodeGroup saved = groupRepository.save(CodeGroup.create(request.groupCode(), request.name(), request.description()));
        return GroupResponse.of(saved, List.of());
    }

    @Transactional
    public GroupResponse updateGroup(String groupCode, UpdateGroupRequest request) {
        CodeGroup group = getGroup(groupCode);
        group.update(request.name(), request.description());
        return GroupResponse.of(group, List.of());
    }

    @Transactional
    public void deleteGroup(String groupCode) {
        CodeGroup group = getGroup(groupCode);
        if (codeRepository.existsByGroupGroupCode(groupCode)) {
            throw new ApiException(ErrorCode.CODE_GROUP_HAS_CODES);
        }
        groupRepository.delete(group);
    }

    @Transactional
    public CodeResponse createCode(String groupCode, CreateCodeRequest request) {
        CodeGroup group = getGroup(groupCode);
        if (codeRepository.existsByGroupGroupCodeAndCode(groupCode, request.code())) {
            throw new ApiException(ErrorCode.CODE_DUPLICATED);
        }
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : codeRepository.findByGroupGroupCodeOrderBySortOrderAscIdAsc(groupCode).stream()
                        .mapToInt(CommonCode::getSortOrder).max().orElse(0) + 1;
        return CodeResponse.from(codeRepository.save(CommonCode.create(group, request.code(), request.name(), sortOrder)));
    }

    @Transactional
    public CodeResponse updateCode(Long id, UpdateCodeRequest request) {
        CommonCode code = codeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CODE_NOT_FOUND));
        code.update(request.name(), request.sortOrder(), request.useYn());
        return CodeResponse.from(code);
    }

    /**
     * 드래그앤드랍 순서 변경. 같은 그룹 안에서 position 위치로 옮기고, 그 그룹 코드들의 순서를 1..n 로 다시 매긴다.
     * 순서에 구멍이 남지 않아 화면의 "순서" 값이 항상 깔끔하다. (메뉴 이동과 같은 방식)
     */
    @Transactional
    public void moveCode(Long id, int position) {
        CommonCode code = codeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CODE_NOT_FOUND));

        List<CommonCode> siblings = new java.util.ArrayList<>(
                codeRepository.findByGroupGroupCodeOrderBySortOrderAscIdAsc(code.getGroupCode()).stream()
                        .filter(c -> !c.getId().equals(id))
                        .toList());
        int pos = Math.min(position, siblings.size());
        siblings.add(pos, code);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).changeSortOrder(i + 1);
        }
    }

    @Transactional
    public void deleteCode(Long id) {
        CommonCode code = codeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CODE_NOT_FOUND));
        codeRepository.delete(code);
    }

    private CodeGroup getGroup(String groupCode) {
        return groupRepository.findById(groupCode)
                .orElseThrow(() -> new ApiException(ErrorCode.CODE_GROUP_NOT_FOUND));
    }
}
