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
                "battleRecordList",
                "battleRecordDetail"
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

    /** 몬스터 목록 — 신규 몬스터가 몇 달에 한 번 추가되므로 24시간 캐시. 신규 sync 시 evict. */
    @Bean("monsterListCacheManager")
    public CacheManager monsterListCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of("monsterList"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(java.time.Duration.ofHours(24))
                .recordStats());
        return cacheManager;
    }

    /** 몬스터 상세(상성·추천·히스토리) — view_battle_deck_info 풀스캔 비용이 크므로 10분 캐시. */
    @Bean("monsterDetailCacheManager")
    public CacheManager monsterDetailCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of(
                "monsterDetailBasic",
                "monsterDetailRecommended",
                "monsterDetailHistory",
                "monsterDetailRecentBattles"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(300)
                .expireAfterWrite(java.time.Duration.ofMinutes(10))
                .recordStats());
        return cacheManager;
    }
}
