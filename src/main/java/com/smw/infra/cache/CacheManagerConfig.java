package com.smw.infra.cache;

import java.util.Arrays;

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
                "monsterList",
                "monsterInfo"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(shortLivedMaximumSize)
                .expireAfterWrite(java.time.Duration.ofMinutes(shortLivedExpireAfterWriteMinutes))
                .recordStats());
        return cacheManager;
    }
}
