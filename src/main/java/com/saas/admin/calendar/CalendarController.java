package com.saas.admin.calendar;

import com.saas.admin.adminaccount.AdminAccountService;
import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.calendar.dto.CalendarDtos.*;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "8. 달력", description = "일정(전체/팀별/개인)과 달력 권한. 범위별 보기·작성 권한을 서버가 강제한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final CalendarPermissionService calendarPermissionService;
    private final HolidayService holidayService;
    private final AdminAccountService adminAccountService;
    private final com.saas.admin.permission.PermissionService permissionService;

    private AdminAccount me(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return adminAccountService.get(principal.empNo());
    }

    // ---- 일정 ----

    @Operation(summary = "월별 일정 조회", description = "내가 볼 수 있는 일정만 온다(전체=권한/팀=내 팀/개인=내 것).")
    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> events(@RequestParam int year, @RequestParam int month,
                                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(calendarService.events(year, month, me(principal)));
    }

    @Operation(summary = "내 달력 권한", description = "내가 가진 달력 권한키 목록(ALL_VIEW 등). 화면이 필터·작성 버튼 노출에 쓴다.")
    @GetMapping("/my-perms")
    public ResponseEntity<Set<String>> myPerms(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(calendarPermissionService.myPermissions(me(principal)));
    }

    @Operation(summary = "월별 공휴일", description = "한국 공휴일(공공데이터 API 캐싱). 권한과 무관하게 모두에게 표시된다.")
    @GetMapping("/holidays")
    public ResponseEntity<List<HolidayResponse>> holidays(@RequestParam int year, @RequestParam int month,
                                                          @AuthenticationPrincipal AuthPrincipal principal) {
        me(principal); // 인증만 확인
        List<HolidayResponse> list = holidayService.month(year, month).stream()
                .map(h -> new HolidayResponse(h.getDate(), h.getName())).toList();
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "일정 생성", description = "범위별 작성 권한이 있어야 한다. TEAM 은 내 소속 팀에 걸린다.")
    @PostMapping("/events")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.create(request, me(principal)));
    }

    @Operation(summary = "일정 수정", description = "작성자 본인(또는 슈퍼)만. 범위는 못 바꾼다.")
    @PatchMapping("/events/{id}")
    public ResponseEntity<EventResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateEventRequest request,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(calendarService.update(id, request, me(principal)));
    }

    @Operation(summary = "일정 삭제", description = "작성자 본인(또는 슈퍼)만.")
    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        calendarService.delete(id, me(principal));
        return ResponseEntity.noContent().build();
    }

    // ---- 권한 관리 (권한관리 메뉴 접근 권한이 있는 관리자) ----

    @Operation(summary = "주체별 달력 권한 조회", description = "부서(DEPT) 또는 부서+직책(DEPT_TITLE)의 달력 권한키 목록.")
    @GetMapping("/permissions")
    public ResponseEntity<List<String>> getPerms(@RequestParam String subjectType, @RequestParam String subjectKey,
                                                 @AuthenticationPrincipal AuthPrincipal principal) {
        requirePermissionMenu(principal);
        return ResponseEntity.ok(calendarPermissionService.permissionsOf(subjectType, subjectKey));
    }

    @Operation(summary = "주체별 달력 권한 저장", description = "보낸 permKeys 로 통째 교체. 직책은 부서 부분집합이어야 한다.")
    @PutMapping("/permissions")
    public ResponseEntity<Void> putPerms(@RequestBody CalendarPermRequest request,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        requirePermissionMenu(principal);
        calendarPermissionService.replacePermissions(request.subjectType(), request.subjectKey(), request.permKeys());
        return ResponseEntity.noContent().build();
    }

    /**
     * 달력 권한 설정은 권한관리 화면(/permissions)에서 이뤄진다 — 그 메뉴에 접근 권한이 있는 관리자만 허용.
     * (메뉴 권한과 동일한 통제. 슈퍼는 전 메뉴 접근이므로 당연히 포함)
     */
    private void requirePermissionMenu(AuthPrincipal principal) {
        if (!permissionService.hasMenuAccess(me(principal), "/permissions")) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
