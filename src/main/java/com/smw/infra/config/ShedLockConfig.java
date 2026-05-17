package com.smw.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * ShedLock 설정 — Redis 기반 분산 락으로 {@link com.smw.monster.batch.queue.BatchQueueProducerService}
 * 의 단일 실행을 보장한다. K8s + 로컬 서버가 동시에 기동돼도 Producer는 1대만 실행된다.
 * <p>
 * {@code @EnableSchedulerLock}의 {@code defaultLockAtMostFor}: 최대 락 보유 시간.
 * 서버가 비정상 종료되어도 이 시간 후에는 락이 자동 해제된다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "55s")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}
