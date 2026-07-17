package com.saas.admin.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.saas.admin.calendar.domain.Holiday;
import com.saas.admin.calendar.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국 공휴일. 공공데이터포털(한국천문연구원 특일정보)의 getRestDeInfo 를 <b>연 단위로</b> 호출해
 * DB 에 캐싱한다. 공휴일은 확정되면 안 바뀌므로 한 해는 한 번만 가져온다.
 * <p>
 * 서비스키는 이미 URL 인코딩된 값이라 {@link URI#create}로 넘겨 <b>재인코딩을 막는다</b>
 * (재인코딩하면 %2F 가 %252F 로 깨진다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private static final String BASE =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HolidayRepository holidayRepository;
    private final RestClient restClient = RestClient.create();

    @Value("${holiday.service-key:}")
    private String serviceKey;

    /** 그 달 공휴일. 아직 안 가져온 해면 먼저 그 해 전체를 가져와 저장한다. */
    @Transactional
    public List<Holiday> month(int year, int month) {
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        LocalDate dec31 = LocalDate.of(year, 12, 31);
        if (!holidayRepository.existsByDateBetween(jan1, dec31)) {
            fetchYear(year);
        }
        LocalDate first = LocalDate.of(year, month, 1);
        return holidayRepository.findByDateBetweenOrderByDateAsc(first, first.plusMonths(1).minusDays(1));
    }

    /** 그 해 공휴일을 API 로 가져와 저장한다. 실패해도 예외를 던지지 않는다(달력은 공휴일 없이라도 떠야 한다). */
    private void fetchYear(int year) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("[공휴일] service-key 가 없어 {}년 공휴일을 가져오지 못했다.", year);
            return;
        }
        try {
            String url = BASE + "?serviceKey=" + serviceKey
                    + "&solYear=" + year + "&numOfRows=100&_type=json";
            JsonNode root = restClient.get().uri(URI.create(url)).retrieve().body(JsonNode.class);

            JsonNode items = root.path("response").path("body").path("items").path("item");
            List<Holiday> holidays = new ArrayList<>();
            for (JsonNode item : itemsAsArray(items)) {
                // isHoliday = Y 인 것만(실제 쉬는 날). locdate = yyyyMMdd 정수.
                if (!"Y".equals(item.path("isHoliday").asText())) {
                    continue;
                }
                LocalDate date = LocalDate.parse(item.path("locdate").asText(), LOCDATE);
                String name = item.path("dateName").asText();
                holidays.add(Holiday.of(date, name));
            }
            holidayRepository.saveAll(holidays);
            log.info("[공휴일] {}년 공휴일 {}건 저장.", year, holidays.size());
        } catch (Exception e) {
            log.warn("[공휴일] {}년 공휴일 조회 실패: {}", year, e.getMessage());
        }
    }

    /** item 은 결과가 1건이면 객체, 여러 건이면 배열, 없으면 누락 — 셋 다 안전하게 순회. */
    private Iterable<JsonNode> itemsAsArray(JsonNode items) {
        if (items.isArray()) {
            return items;
        }
        if (items.isObject()) {
            return List.of(items);
        }
        return List.of();
    }
}
