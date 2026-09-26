package com.saas.admin.homeinquiry;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.homeinquiry.domain.HomeInquiry;
import com.saas.admin.homeinquiry.domain.HomeInquiryStatus;
import com.saas.admin.homeinquiry.domain.SiteType;
import com.saas.admin.homeinquiry.dto.HomeInquiryDtos.*;
import com.saas.admin.homeinquiry.repository.HomeInquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 홈페이지 문의 접수·관리.
 * <p>
 * 등록은 <b>무인증 공개 API</b>가 부른다. 그래서 두 가지를 서비스가 직접 챙긴다.
 * <ol>
 *   <li>같은 IP 의 도배 차단 — 공개 폼이 메일 발송을 유발하므로 그냥 두면 스팸 발사대가 된다</li>
 *   <li>알림 메일 실패가 접수를 되돌리지 않게 분리 — 저장이 끝난 뒤 별도 스레드에서 보낸다</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeInquiryService {

    /** 같은 IP 에서 이 시간(분) 안에 */
    private static final int THROTTLE_MINUTES = 10;
    /** 이 건수를 넘겨 넣으면 막는다. 사람이 문의를 10분에 5번 넘게 넣을 일은 없다. */
    private static final int THROTTLE_MAX = 5;

    /** 요청서 첨부 상한. 화면(RequestForm)과 같은 값이어야 한다. */
    private static final int MAX_IMAGES = 5;

    private final HomeInquiryRepository repository;
    private final com.saas.admin.file.FileUploadService fileUploadService;
    /** 알림은 직접 부르지 않고 이벤트로 넘긴다 — 커밋 뒤에 나가야 하기 때문이다. */
    private final org.springframework.context.ApplicationEventPublisher events;
    /** 수신 주소 조회에만 쓴다(발송은 이벤트가 맡는다). */
    private final HomeInquiryNotifier recipientReader;

    /** 같은 IP 가 짧은 시간에 반복해 넣는 것을 막는다. 공개 폼이 메일 발송을 유발하기 때문이다. */
    private void throttle(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        long recent = repository.countByClientIpAndCreatedAtAfter(
                clientIp, LocalDateTime.now().minusMinutes(THROTTLE_MINUTES));
        if (recent >= THROTTLE_MAX) {
            log.warn("[홈문의] 도배 차단. ip={}, 최근 {}분 {}건", clientIp, THROTTLE_MINUTES, recent);
            throw new ApiException(ErrorCode.HOME_INQUIRY_TOO_MANY);
        }
    }

    @Transactional
    public CreateResponse create(CreateRequest req, String clientIp) {
        throttle(clientIp);

        SiteType siteType = parseSiteType(req.siteType());
        String interests = (req.interests() == null || req.interests().isEmpty())
                ? null : String.join(", ", req.interests());

        HomeInquiry saved = repository.save(HomeInquiry.create(
                siteType,
                req.name().trim(),
                trimToNull(req.phone()),
                req.email().trim(),
                subjectOf(req, siteType),
                trimToNull(req.content()),
                trimToNull(req.storeName()),
                trimToNull(req.storeSize()),
                trimToNull(req.openTiming()),
                interests,
                clientIp));

        // 메일은 커밋이 끝난 뒤 별도 스레드에서 나간다. 실패해도 접수는 남는다.
        events.publishEvent(new HomeInquiryCreatedEvent(saved.getId()));

        return new CreateResponse(saved.getId());
    }

    /**
     * 디자인 시안 요청서(/design) 접수.
     * <p>
     * 예전에는 카페24의 {@code public/mail.php} 가 받아 메일을 보냈다. 사이트가 EC2(nginx)로
     * 옮겨오면서 PHP 가 없어져 <b>POST 가 405 로 떨어지고 있었다.</b> 그래서 일반 문의와 같은
     * 경로(저장 + 공통코드 수신자에게 알림)로 들어오게 옮겼다.
     * <p>
     * 첨부 이미지는 S3(꺼져 있으면 DB)로 올리고 URL 만 문의에 붙인다. 메일에 원본을 실어 보내지
     * 않는 이유는, 5MB 짜리 다섯 장이 붙으면 받는 쪽 메일함에서 거절당하기 때문이다.
     */
    @Transactional
    public CreateResponse createDesignRequest(DesignRequest req, java.util.List<MultipartFile> images,
                                              String clientIp) {
        // 화면에 숨겨 둔 칸이 채워져 왔다 = 봇이다. 조용히 성공으로 돌려보낸다
        // (거부하면 봇이 값을 바꿔가며 다시 온다).
        if (req.honeypot() != null && !req.honeypot().isBlank()) {
            log.warn("[디자인요청] 허니팟에 값이 들어왔다. 접수하지 않고 성공으로 응답한다. ip={}", clientIp);
            return new CreateResponse(null);
        }

        throttle(clientIp);

        List<String> urls = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image == null || image.isEmpty()) continue;
                if (urls.size() >= MAX_IMAGES) break;
                // 형식·용량 검사와 리사이즈·압축은 업로드 서비스가 한다(관리자 업로드와 같은 규칙).
                urls.add(fileUploadService.upload(image));
            }
        }

        HomeInquiry saved = repository.save(HomeInquiry.create(
                SiteType.DESIGN,
                req.name().trim(),
                trimToNull(req.phone()),
                req.email().trim(),
                subjectOfDesign(req),
                designContent(req),
                null, null, null, null,
                clientIp));
        saved.attachImages(urls);

        events.publishEvent(new HomeInquiryCreatedEvent(saved.getId()));
        return new CreateResponse(saved.getId());
    }

    /** 고른 시안이 제목이 된다. 안 고르고 보내는 경우도 있어 그때는 상담 요청으로 적는다. */
    private static String subjectOfDesign(DesignRequest req) {
        String concept = trimToNull(req.concept());
        return "[디자인 시안 요청] " + (concept == null ? "상담 요청" : concept);
    }

    /**
     * 본문. 담당자가 이 글만 보고 바로 작업에 들어갈 수 있게 고른 값들을 한자리에 모은다.
     * (mail.php 가 메일 본문에 적어 주던 것과 같은 항목이다)
     */
    private static String designContent(DesignRequest req) {
        StringBuilder sb = new StringBuilder();
        line(sb, "선택 시안", req.concept(), "고르지 않음 (상담 요청)");
        line(sb, "포인트 색", req.accent(), null);
        line(sb, "넣을 메뉴", req.menus(), null);
        if (req.texts() != null && !req.texts().isBlank()) {
            sb.append("\n[직접 입력한 문구]\n").append(req.texts().trim()).append('\n');
        }
        if (req.note() != null && !req.note().isBlank()) {
            sb.append("\n[요구사항]\n").append(req.note().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private static void line(StringBuilder sb, String label, String value, String fallback) {
        String v = trimToNull(value);
        if (v == null) v = fallback;
        if (v != null) sb.append(label).append(": ").append(v).append('\n');
    }

    @Transactional(readOnly = true)
    public PageResponse list(String siteType, String status, int page, int size) {
        SiteType site = (siteType == null || siteType.isBlank() || "ALL".equalsIgnoreCase(siteType))
                ? null : parseSiteType(siteType);
        HomeInquiryStatus st = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null : parseStatus(status);

        Page<HomeInquiry> found = repository.search(site, st, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
        List<Summary> content = found.getContent().stream().map(Summary::from).toList();

        return new PageResponse(
                content, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages(),
                repository.countByStatus(HomeInquiryStatus.NEW),
                repository.countByStatus(HomeInquiryStatus.IN_PROGRESS),
                repository.countByStatus(HomeInquiryStatus.DONE));
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return Detail.from(find(id));
    }

    @Transactional
    public Detail changeStatus(Long id, StatusRequest req) {
        HomeInquiry q = find(id);
        q.changeStatus(parseStatus(req.status()), req.memo());
        return Detail.from(q);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    /** 지금 알림이 나갈 주소들. 콘솔이 "누가 받는지" 를 보여주는 데 쓴다. */
    @Transactional(readOnly = true)
    public List<String> recipients() {
        return recipientReader.recipients();
    }

    private HomeInquiry find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.HOME_INQUIRY_NOT_FOUND));
    }

    /** 도입 문의는 화면이 제목을 받지 않는다 — 매장명으로 만든다. */
    private static String subjectOf(CreateRequest req, SiteType siteType) {
        String subject = trimToNull(req.subject());
        if (subject != null) return subject;
        if (siteType == SiteType.EXPRISM) {
            String store = trimToNull(req.storeName());
            return "[EXPRISM 도입 문의] " + (store == null ? req.name().trim() : store);
        }
        return "홈페이지 문의 - " + req.name().trim();
    }

    private static SiteType parseSiteType(String value) {
        if (value == null || value.isBlank()) return SiteType.KANCHENJUNGA;
        try {
            return SiteType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 공개 API 라 이상한 값이 올 수 있다. 문의를 잃는 것보다 회사 문의로 받는 편이 낫다.
            log.warn("[홈문의] 알 수 없는 siteType={} → KANCHENJUNGA 로 처리한다.", value);
            return SiteType.KANCHENJUNGA;
        }
    }

    private static HomeInquiryStatus parseStatus(String value) {
        try {
            return HomeInquiryStatus.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
