package com.saas.admin.adminaccount;

import com.saas.admin.adminaccount.dto.AdminDtos.AdminResponse;
import com.saas.admin.adminaccount.dto.AdminDtos.CreateAdminRequest;
import com.saas.admin.adminaccount.dto.AdminDtos.UpdateAdminRequest;

import java.util.Map;
import com.saas.admin.auth.jwt.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "2. 관리자 계정", description = "내부 직원 계정 생성/조회/수정/삭제. 사번은 서버가 채번한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/admins")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(
            summary = "관리자 생성",
            description = """
                    사번은 **서버가 채번한다** — YY + 4자리 순번 (260001 = 2026년 1번째).
                    요청에 사번을 담지 않는다. 동시에 여러 명을 만들어도 번호는 겹치지 않는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성됨. 응답의 empNo 가 발급된 사번이다."),
            @ApiResponse(responseCode = "409", description = "올해 사번 소진(EMPLOYEE_NO_EXHAUSTED)", content = @Content)
    })
    @PostMapping
    public ResponseEntity<AdminResponse> create(@Valid @RequestBody CreateAdminRequest request) {
        AdminResponse created = AdminResponse.from(adminAccountService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(
            summary = "관리자 목록",
            description = "기본은 삭제되지 않은 계정만 반환한다. `includeDeleted=true` 면 삭제된 것도 포함한다.")
    @GetMapping
    public ResponseEntity<Page<AdminResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @PageableDefault(size = 20, sort = "empNo", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminAccountService.list(includeDeleted, pageable).map(AdminResponse::from));
    }

    @Operation(summary = "관리자 단건 조회", description = "삭제된 관리자도 조회된다(감사 추적용). deleted 필드로 구분한다.")
    @ApiResponse(responseCode = "404", description = "ADMIN_NOT_FOUND", content = @Content)
    @GetMapping("/{empNo}")
    public ResponseEntity<AdminResponse> get(@PathVariable String empNo) {
        return ResponseEntity.ok(AdminResponse.from(adminAccountService.get(empNo)));
    }

    @Operation(
            summary = "관리자 수정",
            description = """
                    보낸 항목만 바뀐다. **사번과 비밀번호는 여기서 바꿀 수 없다** —
                    사번은 불변이고, 비밀번호는 별도 엔드포인트다(감사 대상이 다르다).

                    상태를 ACTIVE 가 아닌 값으로 바꾸면 그 관리자의 리프레시 토큰이 전부 폐기된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "ADMIN_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "마지막 관리자는 정지할 수 없음(CANNOT_DISABLE_LAST_ADMIN)", content = @Content)
    })
    @PatchMapping("/{empNo}")
    public ResponseEntity<AdminResponse> update(@PathVariable String empNo,
                                                @Valid @RequestBody UpdateAdminRequest request) {
        return ResponseEntity.ok(AdminResponse.from(adminAccountService.update(empNo, request)));
    }

    @Operation(
            summary = "비밀번호 초기화",
            description = """
                    비밀번호를 **기본값(exprism1234!)** 으로 되돌린다. 새 비밀번호를 입력받지 않는다 —
                    초기화하는 사람이 남의 비밀번호를 알고 있어서는 안 되기 때문이다.

                    - 계정 잠금이 함께 풀린다.
                    - 기존 세션(리프레시 토큰)은 모두 끊긴다.
                    - 그 사람은 다음 로그인 때 **반드시 비밀번호를 바꿔야 한다.** 바꾸기 전에는
                      로그인해도 `/api/platform-admin/**` 을 쓸 수 없다(403).
                    """)
    @ApiResponse(responseCode = "200", description = "초기화됨. 응답의 password 를 본인에게 전달한다.")
    @PostMapping("/{empNo}/password/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String empNo) {
        String initial = adminAccountService.resetPassword(empNo);
        return ResponseEntity.ok(Map.of("password", initial));
    }

    @Operation(
            summary = "관리자 삭제 (소프트 삭제)",
            description = """
                    행을 지우지 않는다. `is_deleted = 'Y'` 로 표시하고 누가 언제 지웠는지 남긴다.
                    삭제된 계정은 로그인할 수 없고, 발급돼 있던 리프레시 토큰도 즉시 폐기된다.

                    **자기 자신**과 **마지막으로 남은 관리자**는 삭제할 수 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨"),
            @ApiResponse(responseCode = "404", description = "ADMIN_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "자기 자신(CANNOT_DELETE_SELF) / 마지막 관리자(CANNOT_DISABLE_LAST_ADMIN) / 이미 삭제됨(ADMIN_ALREADY_DELETED)", content = @Content)
    })
    @DeleteMapping("/{empNo}")
    public ResponseEntity<Void> delete(@PathVariable String empNo,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        // 실행자도 사번으로 식별한다. 토큰의 subject 가 곧 사번이다.
        adminAccountService.delete(empNo, principal.empNo());
        return ResponseEntity.noContent().build();
    }
}
