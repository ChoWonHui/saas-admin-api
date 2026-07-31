package com.saas.admin.i18n;

import com.saas.admin.i18n.I18nDtos.TranslateRequest;
import com.saas.admin.i18n.I18nDtos.TranslateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 손님(무인증) 다국어 번역. 손님 화면(saas-client-web)이 메뉴 텍스트를 골라진 언어로 번역해 달라고 부른다.
 * 결과는 서버에서 캐시되므로 같은 문장은 다시 외부 API 를 부르지 않는다.
 */
@Tag(name = "21. 손님 다국어(무인증)", description = "메뉴 텍스트를 대상 언어로 자동 번역한다(캐시).")
@RestController
@RequestMapping("/api/public/i18n")
@RequiredArgsConstructor
public class PublicI18nController {

    private static final int MAX_TEXTS = 300; // 한 요청에 받는 문장 수 상한

    private final TranslationService translationService;

    @Operation(summary = "메뉴 텍스트 번역", description = "원문(한글) 목록을 대상 언어로 번역한 { 원문: 번역문 } 을 돌려준다.")
    @PostMapping("/translate")
    public ResponseEntity<TranslateResponse> translate(@Valid @RequestBody TranslateRequest req) {
        List<String> texts = req.texts() == null ? List.of()
                : req.texts().size() > MAX_TEXTS ? req.texts().subList(0, MAX_TEXTS) : req.texts();
        Map<String, String> map = translationService.translate(req.targetLang(), texts);
        return ResponseEntity.ok(new TranslateResponse(req.targetLang(), map));
    }
}
