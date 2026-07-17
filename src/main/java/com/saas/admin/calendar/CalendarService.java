package com.saas.admin.calendar;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.calendar.domain.CalendarEvent;
import com.saas.admin.calendar.dto.CalendarDtos.*;
import com.saas.admin.calendar.repository.CalendarEventRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.org.domain.Organization;
import com.saas.admin.org.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 달력 일정. 범위(전체/팀/개인)에 따라 가시성과 작성 권한을 서버가 강제한다.
 * <ul>
 *   <li>볼 수 있는 일정 = ALL(ALL_VIEW 권한) ∪ 내 팀 TEAM(TEAM_VIEW) ∪ 내 PERSONAL(PERSONAL_VIEW)</li>
 *   <li>작성 = 그 범위의 _WRITE 권한. TEAM 은 내 소속 팀에만.</li>
 *   <li>수정·삭제 = 작성자 본인 또는 슈퍼.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final String NOT_DELETED = "N";

    private final CalendarEventRepository eventRepository;
    private final CalendarPermissionService calendarPermissionService;
    private final OrganizationRepository orgRepository;
    private final AdminAccountRepository adminRepository;

    /** 한 달(±1주) 안의, 이 사원이 볼 수 있는 일정. */
    @Transactional(readOnly = true)
    public List<EventResponse> events(int year, int month, AdminAccount viewer) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate rangeStart = first.minusDays(7);
        LocalDate rangeEnd = first.plusMonths(1).plusDays(7);

        Set<String> perms = calendarPermissionService.myPermissions(viewer);
        List<CalendarEvent> events = eventRepository
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(rangeEnd, rangeStart);

        // 이름 조회용 캐시 (조직/작성자)
        Map<Long, String> orgNames = orgRepository.findAll().stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName));
        Map<String, String> adminNames = adminRepository.findByDeletedOrderByEmpNoAsc(NOT_DELETED).stream()
                .collect(Collectors.toMap(AdminAccount::getEmpNo, AdminAccount::getName, (a, b) -> a));

        return events.stream()
                .filter(e -> canView(e, viewer, perms))
                .map(e -> new EventResponse(
                        e.getId(), e.getTitle(), e.getStartDate(), e.getEndDate(),
                        e.isAllDay(), e.getStartTime(), e.getEndTime(), e.getScope(),
                        e.getOrgId(), e.getOrgId() == null ? null : orgNames.get(e.getOrgId()),
                        e.getOwnerEmpNo(), adminNames.getOrDefault(e.getOwnerEmpNo(), e.getOwnerEmpNo()),
                        e.getDescription(), viewer.isSuper() || e.isOwnedBy(viewer.getEmpNo())))
                .toList();
    }

    private boolean canView(CalendarEvent e, AdminAccount viewer, Set<String> perms) {
        return switch (e.getScope()) {
            case "ALL" -> perms.contains("ALL_VIEW");
            case "TEAM" -> perms.contains("TEAM_VIEW") && Objects.equals(e.getOrgId(), viewer.getOrgId());
            case "PERSONAL" -> perms.contains("PERSONAL_VIEW") && e.isOwnedBy(viewer.getEmpNo());
            default -> false;
        };
    }

    @Transactional
    public EventResponse create(CreateEventRequest req, AdminAccount viewer) {
        Set<String> perms = calendarPermissionService.myPermissions(viewer);
        String scope = req.scope();
        Long orgId = null;

        switch (scope) {
            case "ALL" -> requireWrite(perms, "ALL_WRITE");
            case "TEAM" -> {
                requireWrite(perms, "TEAM_WRITE");
                if (viewer.getOrgId() == null) throw new ApiException(ErrorCode.CALENDAR_NO_TEAM);
                orgId = viewer.getOrgId();
            }
            case "PERSONAL" -> requireWrite(perms, "PERSONAL_WRITE");
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }

        CalendarEvent saved = eventRepository.save(CalendarEvent.create(
                req.title(), req.startDate(), req.endDate(),
                req.allDay(), req.startTime(), req.endTime(),
                scope, orgId, viewer.getEmpNo(), req.description()));
        return toResponse(saved, viewer);
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest req, AdminAccount viewer) {
        CalendarEvent e = getOwnedOrSuper(id, viewer);
        e.update(req.title(), req.startDate(), req.endDate(),
                req.allDay(), req.startTime(), req.endTime(), req.description());
        return toResponse(e, viewer);
    }

    @Transactional
    public void delete(Long id, AdminAccount viewer) {
        CalendarEvent e = getOwnedOrSuper(id, viewer);
        eventRepository.delete(e);
    }

    private CalendarEvent getOwnedOrSuper(Long id, AdminAccount viewer) {
        CalendarEvent e = eventRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!viewer.isSuper() && !e.isOwnedBy(viewer.getEmpNo())) {
            throw new ApiException(ErrorCode.CALENDAR_NOT_OWNER);
        }
        return e;
    }

    private void requireWrite(Set<String> perms, String key) {
        if (!perms.contains(key)) {
            throw new ApiException(ErrorCode.CALENDAR_WRITE_DENIED);
        }
    }

    private EventResponse toResponse(CalendarEvent e, AdminAccount viewer) {
        Function<Long, String> orgName = id -> id == null ? null :
                orgRepository.findById(id).map(Organization::getName).orElse(null);
        String ownerName = adminRepository.findById(e.getOwnerEmpNo())
                .map(AdminAccount::getName).orElse(e.getOwnerEmpNo());
        return new EventResponse(e.getId(), e.getTitle(), e.getStartDate(), e.getEndDate(),
                e.isAllDay(), e.getStartTime(), e.getEndTime(), e.getScope(),
                e.getOrgId(), orgName.apply(e.getOrgId()), e.getOwnerEmpNo(), ownerName,
                e.getDescription(), viewer.isSuper() || e.isOwnedBy(viewer.getEmpNo()));
    }
}
