package com.saas.admin.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 공지 에디터 이미지 저장용 S3 클라이언트.
 *
 * <p>food-biz 프로젝트와 "같은" 버킷(foodbiz-uploads)·CloudFront 를 공유한다.
 * 저장 키는 {@code storage.s3.key-prefix}(= saas-admin) 로 분리하므로 food-biz 파일과 섞이지 않는다.
 *
 * <p>{@code storage.s3.enabled=false} 이면 이 빈이 아예 만들어지지 않는다.
 * 그 경우 {@link FileUploadService} 는 S3 가 없다고 보고 업로드 API 를 503 으로 막고,
 * 프런트는 base64 삽입으로 폴백한다 — 즉 S3 없이도 에디터 자체는 동작한다.
 */
@Configuration
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class S3Config {

    @Value("${storage.s3.access-key}")
    private String accessKey;

    @Value("${storage.s3.secret-key}")
    private String secretKey;

    @Value("${storage.s3.region:ap-northeast-2}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}
