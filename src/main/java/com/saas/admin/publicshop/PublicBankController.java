package com.saas.admin.publicshop;

import com.saas.admin.code.repository.CommonCodeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 손님(무인증) 은행 송금 런처 목록.
 * <p>
 * 공통코드 {@code BANK_CD} 한 그룹에서 내려준다.
 * <ul>
 *   <li>{@code code} — 은행코드</li>
 *   <li>{@code code_name} — 은행명(표시)</li>
 *   <li>{@code remark}(비고) — <b>iOS용</b> 실행 URL</li>
 *   <li>{@code remark2}(비고2) — <b>안드로이드용</b> 실행 URL</li>
 * </ul>
 * URL 에는 치환자 {@code {bank}} {@code {accountNo}} {@code {amount}} 를 쓸 수 있고, 실제 값 채우기·기기별
 * URL 선택은 손님 화면이 한다. 사용중(use_yn='Y')이면서 <b>둘 중 하나라도 URL이 있는</b> 은행만, 순서대로.
 */
@Tag(name = "22. 은행 송금 런처(무인증)", description = "은행 선택 시 열 URL 목록(BANK_CD 비고=iOS / 비고2=Android).")
@RestController
@RequestMapping("/api/public/bank-launchers")
@RequiredArgsConstructor
public class PublicBankController {

    private static final String GROUP_BANK = "BANK_CD";

    private final CommonCodeRepository codeRepository;

    /** 은행 런처 한 건 — 코드, 표시명, iOS URL(비고), 안드로이드 URL(비고2). */
    public record BankLauncher(String code, String name, String url, String androidUrl) {
    }

    @Operation(summary = "은행 송금 런처 목록", description = "BANK_CD 중 비고/비고2(URL)가 있는 사용중 은행만. url=iOS(비고), androidUrl=Android(비고2).")
    @GetMapping
    public List<BankLauncher> list() {
        return codeRepository.findByGroupGroupCodeOrderBySortOrderAscIdAsc(GROUP_BANK).stream()
                .filter(c -> "Y".equals(c.getUseYn()))
                .filter(c -> notBlank(c.getRemark()) || notBlank(c.getRemark2()))
                .map(c -> new BankLauncher(c.getCode(), c.getName(), trimOrNull(c.getRemark()), trimOrNull(c.getRemark2())))
                .collect(Collectors.toList());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimOrNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}
