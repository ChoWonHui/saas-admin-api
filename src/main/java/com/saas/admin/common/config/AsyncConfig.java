package com.saas.admin.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * {@code @Async} 활성화. 지금 쓰는 곳은 홈페이지 문의 알림 메일 하나다.
 * <p>
 * 메일을 동기로 보내면 SMTP 타임아웃(5초)이 수신자 수만큼 쌓여 방문자가 그만큼 기다린다.
 * 문의는 이미 저장된 뒤이므로 메일은 뒤에서 보내면 된다.
 * <p>
 * 이 서버는 RAM 이 좁아(컨테이너 400MB) 스레드를 넉넉히 잡지 않는다. 큐가 차면
 * 호출한 스레드가 직접 실행({@code CallerRunsPolicy})해 알림을 잃지 않고 자연히 속도를 늦춘다.
 */
@Configuration
@EnableAsync
@EnableScheduling   // 메일함 IMAP 동기화(MailSyncService)가 주기 실행된다
public class AsyncConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
