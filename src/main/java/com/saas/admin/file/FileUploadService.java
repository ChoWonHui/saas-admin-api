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

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    /** 저장 이미지의 최대 가로·세로(px). 이보다 크면 비율을 유지하며 이 안으로 줄여 저장한다. */
    private static final int MAX_IMAGE_DIMENSION = 1600;

    @Value("${storage.s3.bucket:}")
    private String bucket;

    @Value("${storage.s3.cdn-url:}")
    private String cdnUrl;

    @Value("${storage.s3.key-prefix:saas-admin}")
    private String keyPrefix;

    /** enabled=false 면 S3Config 빈이 없어 null 로 주입된다 → isEnabled()=false. */
    @Autowired(required = false)
    private S3Client s3Client;

    /** S3 폴백용 DB 이미지 저장소(가게꾸미기 이미지와 공용). */
    @Autowired
    private com.saas.admin.decorate.repository.DecorateImageRepository decorateImageRepository;

    public boolean isEnabled() {
        return s3Client != null && bucket != null && !bucket.isBlank();
    }

    /** S3 없이 이미지 바이트를 DB 에 저장하고 공개 URL 을 돌려준다. */
    private String storeToDb(byte[] bytes, String contentType, String name) {
        com.saas.admin.decorate.domain.DecorateImage img =
                decorateImageRepository.save(com.saas.admin.decorate.domain.DecorateImage.create(contentType, bytes));
        String url = "/api/public/decorate/images/" + img.getId();
        log.info("이미지 DB 저장(S3 미사용): {} ({} bytes) -> {}", name, bytes.length, url);
        return url;
    }

    /**
     * 이미지를 S3 에 올리고 CDN URL 을 돌려준다.
     *
     * @throws ApiException FILE_STORAGE_DISABLED / FILE_EMPTY / FILE_TYPE_NOT_ALLOWED /
     *                      FILE_TOO_LARGE / FILE_UPLOAD_FAILED
     */
    public String upload(MultipartFile file) {
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

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        // 최대 가로·세로를 넘으면 비율을 유지하며 줄인다.
        bytes = resizeIfNeeded(bytes, contentType);

        // S3 가 꺼져 있으면 DB 이미지 저장소로 폴백한다(가게꾸미기 이미지와 동일하게 /api/public/decorate/images 로 서빙).
        if (!isEnabled()) {
            return storeToDb(bytes, contentType, file.getOriginalFilename());
        }

        String key = buildKey(file.getOriginalFilename(), contentType);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            log.error("S3 이미지 업로드 실패: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String url = cdnUrl + "/" + key;
        log.info("이미지 업로드 완료: {} ({} bytes) -> {}",
                file.getOriginalFilename(), bytes.length, url);
        return url;
    }

    /**
     * 이미지가 최대 가로·세로({@link #MAX_IMAGE_DIMENSION})를 넘으면 비율을 유지하며 그 안으로 줄인다.
     * JPEG/PNG 만 처리하고, ImageIO 로 못 읽는 형식(webp 등)·GIF(애니메이션)는 원본을 그대로 둔다.
     * 실패해도 원본 바이트를 돌려줘 업로드가 끊기지 않게 한다.
     */
    private byte[] resizeIfNeeded(byte[] data, String contentType) {
        boolean jpeg = "image/jpeg".equals(contentType);
        boolean png = "image/png".equals(contentType);
        if (!jpeg && !png) {
            return data; // gif/webp 등은 그대로
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) {
                return data;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= MAX_IMAGE_DIMENSION && h <= MAX_IMAGE_DIMENSION) {
                return data; // 이미 작음
            }
            double scale = Math.min((double) MAX_IMAGE_DIMENSION / w, (double) MAX_IMAGE_DIMENSION / h);
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage dst = new BufferedImage(nw, nh, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(dst, png ? "png" : "jpeg", out);
            byte[] resized = out.toByteArray();
            log.info("이미지 축소: {}x{} -> {}x{} ({} -> {} bytes)", w, h, nw, nh, data.length, resized.length);
            return resized.length > 0 ? resized : data;
        } catch (Exception e) {
            log.warn("이미지 축소 실패(원본 유지): {}", e.getMessage());
            return data;
        }
    }

    /**
     * 외부 URL(검색으로 고른 이미지)을 내려받아 S3 에 올리고 CDN URL 을 돌려준다.
     * S3 가 꺼져 있으면 원본 URL 을 그대로 돌려준다(폴백).
     */
    public String uploadFromUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApiException(ErrorCode.FILE_EMPTY);
        }
        if (!isEnabled()) {
            return sourceUrl; // 저장소 없으면 원본 링크 그대로 사용
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            java.net.http.HttpResponse<byte[]> res = client.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(sourceUrl)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new ApiException(ErrorCode.FILE_UPLOAD_FAILED);
            }
            String contentType = res.headers().firstValue("content-type").orElse("image/jpeg");
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) contentType = "image/jpeg";
            byte[] bytes = res.body();
            if (bytes.length == 0) throw new ApiException(ErrorCode.FILE_EMPTY);
            if (bytes.length > MAX_IMAGE_SIZE) throw new ApiException(ErrorCode.FILE_TOO_LARGE);

            String key = buildKey(sourceUrl, contentType);
            s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
            String url = cdnUrl + "/" + key;
            log.info("URL 이미지 저장 완료: {} ({} bytes) -> {}", sourceUrl, bytes.length, url);
            return url;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("URL 이미지 저장 실패: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.FILE_UPLOAD_FAILED);
        }
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
