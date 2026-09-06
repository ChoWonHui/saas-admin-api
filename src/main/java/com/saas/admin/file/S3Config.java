package com.saas.admin.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 공지 에디터·업체 이미지 저장용 S3 클라이언트.
 *
 * <p>food-biz 프로젝트와 "같은" 버킷(foodbiz-uploads)·CloudFront 를 공유한다.
 * 저장 키는 {@code storage.s3.key-prefix}(= saas-admin) 로 분리하므로 food-biz 파일과 섞이지 않는다.
 *
 * <p>{@code storage.s3.enabled=false} 이면 이 빈이 아예 만들어지지 않는다.
 * 그 경우 {@link FileUploadService} 는 이미지를 DB(decorate_image)에 담는데,
 * MySQL 의 max_allowed_packet(기본 4MB)을 넘는 사진에서 업로드가 실패한다.
 * 운영에서는 S3 를 켜는 것이 정상 경로다.
 *
 * <p><b>자격 증명</b>은 두 가지를 지원한다.
 * <ol>
 *   <li>access-key / secret-key 를 주면 그 값을 쓴다.</li>
 *   <li>비워 두면 AWS 기본 체인을 쓴다 → EC2 에 붙은 <b>IAM 역할</b>로 인증한다.
 *       서버에 비밀키를 두지 않아도 되고, 키 유출·회전 부담이 없어 이쪽을 권한다.</li>
 * </ol>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class S3Config {

    @Value("${storage.s3.access-key:}")
    private String accessKey;

    @Value("${storage.s3.secret-key:}")
    private String secretKey;

    @Value("${storage.s3.region:ap-northeast-2}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    /**
     * 키가 둘 다 채워져 있을 때만 그 값을 쓴다. 하나라도 비면 기본 체인으로 넘긴다.
     * 기본 체인은 환경변수 → 프로파일 → EC2 인스턴스 IAM 역할 순으로 찾는다.
     */
    private AwsCredentialsProvider credentialsProvider() {
        boolean hasStatic = accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
        if (hasStatic) {
            log.info("S3 자격 증명: 설정에 주어진 액세스 키 사용");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        log.info("S3 자격 증명: 키가 비어 있어 AWS 기본 체인 사용(EC2 IAM 역할 등)");
        return DefaultCredentialsProvider.create();
    }
}
