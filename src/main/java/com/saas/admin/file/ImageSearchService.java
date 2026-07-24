package com.saas.admin.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 무료 이미지 검색(Pixabay) 프록시. 관리자·업체 콘솔이 함께 쓴다. 키는 서버 설정에만 둔다. */
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    @Value("${image-search.pixabay-key:}")
    private String pixabayKey;

    private final ObjectMapper objectMapper;

    public List<Map<String, String>> search(String q, int page) {
        if (pixabayKey == null || pixabayKey.isBlank()) {
            throw new ApiException(ErrorCode.IMAGE_SEARCH_UNAVAILABLE);
        }
        if (q == null || q.isBlank()) {
            return List.of();
        }
        try {
            String url = "https://pixabay.com/api/?key=" + pixabayKey
                    + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                    + "&image_type=photo&safesearch=true&per_page=24&page=" + Math.max(1, page);
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> res = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ApiException(ErrorCode.IMAGE_SEARCH_FAILED);
            }
            JsonNode hits = objectMapper.readTree(res.body()).path("hits");
            List<Map<String, String>> out = new ArrayList<>();
            for (JsonNode h : hits) {
                out.add(Map.of(
                        "thumb", h.path("previewURL").asText(""),
                        "url", h.path("webformatURL").asText("")));
            }
            return out;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.IMAGE_SEARCH_FAILED);
        }
    }
}
