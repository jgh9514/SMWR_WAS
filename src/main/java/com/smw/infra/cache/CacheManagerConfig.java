package com.smw.infra.cache;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheManagerConfig {

    @Value("${smw.cache.short-lived.maximum-size:500}")
    private long shortLivedMaximumSize;

    @Value("${smw.cache.short-lived.expire-after-write-minutes:5}")
    private long shortLivedExpireAfterWriteMinutes;

    @Value("${smw.cache.monster-info.maximum-size:2000}")
    private long monsterInfoMaximumSize;

    @Value("${smw.cache.monster-info.expire-after-write-hours:6}")
    private long monsterInfoExpireAfterWriteHours;

    @Bean("shortLivedCacheManager")
    @Primary
    public CacheManager shortLivedCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
                "accountSummaryLatest",
                "accountSummaryImportList",
                "accountSummaryImportDetail",
                "accountSummaryMonsterList",
                "accountSummaryMonsterCatalog",
                "accountSummaryRuneList",
                "accountSummaryRuneScoreSummary",
                "noticeList",
                "noticeDetail",
                "popupNoticeList",
                "guildSiegeHistory",
                "guildSiegeHistoryCount",
                "monsterList"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(shortLivedMaximumSize)
                .expireAfterWrite(java.time.Duration.ofMinutes(shortLivedExpireAfterWriteMinutes))
                .recordStats());
        return cacheManager;
    }

    @Bean("monsterInfoCacheManager")
    public CacheManager monsterInfoCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of("monsterInfo"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(monsterInfoMaximumSize)
                .expireAfterWrite(java.time.Duration.ofHours(monsterInfoExpireAfterWriteHours))
                .recordStats());
        return cacheManager;
    }
}
