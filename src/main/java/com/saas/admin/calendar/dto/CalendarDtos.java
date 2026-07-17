package com.saas.admin.calendar.dto;

import com.saas.admin.calendar.domain.CalendarEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 달력 일정/권한 관련 요청·응답 DTO 모음. */
public final class CalendarDtos {

    private CalendarDtos() {
    }

    @Schema(description = "일정 생성. scope 는 ALL(전사)/TEAM(팀별)/PERSONAL(개인). allDay=false 면 시간을 쓴다.")
    public record CreateEventRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
            String title,

            @NotNull(message = "시작일은 필수입니다.")
            LocalDate startDate,

            @NotNull(message = "종료일은 필수입니다.")
            LocalDate endDate,

            boolean allDay,
            LocalTime startTime,
            LocalTime endTime,

            @NotNull(message = "범위는 필수입니다.")
            @Pattern(regexp = "ALL|TEAM|PERSONAL", message = "범위는 ALL/TEAM/PERSONAL 이어야 합니다.")
            String scope,

            @Size(max = 500, message = "설명은 500자를 넘을 수 없습니다.")
            String description
    ) {
    }

    @Schema(description = "일정 수정. 제목·기간·시간·설명이 바뀐다. 범위는 바꿀 수 없다(가시성이 달라지므로).")
    public record UpdateEventRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
            String title,

            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,

            boolean allDay,
            LocalTime startTime,
            LocalTime endTime,

            @Size(max = 500, message = "설명은 500자를 넘을 수 없습니다.")
            String description
    ) {
    }

    public record EventResponse(
            Long id, String title, LocalDate startDate, LocalDate endDate,
            boolean allDay, LocalTime startTime, LocalTime endTime,
            String scope, Long orgId, String orgName, String ownerEmpNo, String ownerName,
            String description, boolean editable
    ) {
    }

    @Schema(description = "주체별 달력 권한 저장. permKeys: ALL_VIEW/ALL_WRITE/TEAM_VIEW/TEAM_WRITE/PERSONAL_VIEW/PERSONAL_WRITE.")
    public record CalendarPermRequest(
            @NotBlank String subjectType,   // DEPT | DEPT_TITLE
            @NotBlank String subjectKey,
            List<String> permKeys
    ) {
    }

    public record HolidayResponse(LocalDate date, String name) {
    }
}
