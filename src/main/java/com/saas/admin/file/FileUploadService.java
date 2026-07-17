package com.saas.admin.file;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * 공지 에디터 이미지를 S3 에 올리고 공개(CDN) URL 을 돌려준다.
 *
 * <p>업로드 경로: 브라우저 → 우리 API(멀티파트) → S3. 자격 증명이 서버 밖으로 나가지 않고,
 * 브라우저가 S3 로 직접 PUT 하지 않으므로 S3 CORS 설정도 필요 없다.
 *
 * <p>S3 키: {@code {key-prefix}/notices/images/{yyyy}/{MM}/{UUID}.{ext}}
 * <br>예) {@code saas-admin/notices/images/2026/07/1a2b...c9.png}
 */
@Slf4j
@Service
public class FileUploadService {

    /** 에디터에 넣을 수 있는 이미지 형식. */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /** 이미지 1장 최대 크기(5MB). multipart.max-file-size 와 맞춰 둔다. */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    @Value("${storage.s3.bucket:}")
    private String bucket;

    @Value("${storage.s3.cdn-url:}")
    private String cdnUrl;

    @Value("${storage.s3.key-prefix:saas-admin}")
    private String keyPrefix;

    /** enabled=false 면 S3Config 빈이 없어 null 로 주입된다 → isEnabled()=false. */
    @Autowired(required = false)
    private S3Client s3Client;

    public boolean isEnabled() {
        return s3Client != null && bucket != null && !bucket.isBlank();
    }

    /**
     * 이미지를 S3 에 올리고 CDN URL 을 돌려준다.
     *
     * @throws ApiException FILE_STORAGE_DISABLED / FILE_EMPTY / FILE_TYPE_NOT_ALLOWED /
     *                      FILE_TOO_LARGE / FILE_UPLOAD_FAILED
     */
    public String upload(MultipartFile file) {
        if (!isEnabled()) {
            throw new ApiException(ErrorCode.FILE_STORAGE_DISABLED);
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_EMPTY);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }

        String key = buildKey(file.getOriginalFilename(), contentType);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("S3 이미지 업로드 실패: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String url = cdnUrl + "/" + key;
        log.info("공지 이미지 업로드 완료: {} ({} bytes) -> {}",
                file.getOriginalFilename(), file.getSize(), url);
        return url;
    }

    /** {prefix}/notices/images/{yyyy}/{MM}/{UUID}.{ext} */
    private String buildKey(String originalFilename, String contentType) {
        String ext = extensionOf(originalFilename, contentType);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return String.format("%s/notices/images/%d/%02d/%s%s",
                keyPrefix, today.getYear(), today.getMonthValue(), UUID.randomUUID(), ext);
    }

    /** 원본 파일명의 확장자를 쓰되, 없으면 content-type 으로 유추한다. */
    private String extensionOf(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
            // 방어: 확장자에 경로 구분자나 이상한 문자가 섞이면 버린다.
            if (ext.length() <= 6 && ext.matches("\\.[A-Za-z0-9]+")) {
                return ext.toLowerCase();
            }
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }
}
