package com.smw.infra.cache;

import java.time.Duration;
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

    @Value("${smw.cache.rta-list-read.expire-after-write-minutes:15}")
    private long rtaListReadExpireAfterWriteMinutes;

    @Value("${smw.cache.rta-list-read.maximum-size:3000}")
    private long rtaListReadMaximumSize;

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
                "monsterInfo",
                "rtaDashboardTiers",
                "rtaMonster",
                "rtaRanking",
                "rtaSeasons"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(shortLivedMaximumSize)
                .expireAfterWrite(java.time.Duration.ofMinutes(shortLivedExpireAfterWriteMinutes))
                .recordStats());
        return cacheManager;
    }

    /**
     * RTA 매치 목록·건수·stats — 조회 전용 부하가 크므로 short-lived(5분)보다 긴 TTL.
     * 배치 적재 후 {@link com.smw.rta.cache.RtaCacheEvictor} 로 무효화.
     */
    @Bean("rtaListReadCacheManager")
    public CacheManager rtaListReadCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList("rtaMatchList"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(rtaListReadMaximumSize)
                .expireAfterWrite(Duration.ofMinutes(rtaListReadExpireAfterWriteMinutes))
                .recordStats());
        return cacheManager;
    }

    /** RTA 랭크 컷 라이브 조회 전용 — 1시간 단위 갱신 */
    @Bean("rtaOneHourCacheManager")
    public CacheManager rtaOneHourCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList("rtaRankCutoffLive"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(20)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats());
        return cacheManager;
    }
}
