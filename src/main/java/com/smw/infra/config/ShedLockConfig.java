package com.smw.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * {@code smw.batch.queue.enabled=true} 일 때만 활성화 (기본값 false — 로컬 Redis 없는 환경 보호).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "55s")
@ConditionalOnProperty(name = "smw.batch.queue.enabled", havingValue = "true")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}
