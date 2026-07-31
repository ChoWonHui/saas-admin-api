package com.saas.admin.decorate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 가게 꾸미기용 이미지(머리·모자 그림 등) — DB 저장.
 * S3 가 꺼진 환경에서도 그림판 이미지를 보관·서빙하기 위해 바이트를 그대로 담는다.
 * 작은 PNG(수 KB) 위주라 LONGBLOB 으로 충분하다.
 */
@Entity
@Table(name = "decorate_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecorateImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Lob
    @Column(name = "data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static DecorateImage create(String contentType, byte[] data) {
        DecorateImage img = new DecorateImage();
        img.contentType = contentType;
        img.data = data;
        return img;
    }
}
