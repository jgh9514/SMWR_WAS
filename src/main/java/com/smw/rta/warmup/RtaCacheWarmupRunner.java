package com.smw.rta.warmup;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.smw.rta.service.RtaService;

/**
 * 배포 직후 Caffeine cache cold start 완화.
 * 상위 랭커 N명의 summary·monster-usage를 백그라운드에서 미리 호출해 캐시를 채운다.
 */
@Component
public class RtaCacheWarmupRunner implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(RtaCacheWarmupRunner.class);
    private static final int WARMUP_TOP_N = 20;
    private static final Executor WARMUP_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final RtaService rtaService;

    public RtaCacheWarmupRunner(RtaService rtaService) {
        this.rtaService = rtaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(this::warmup, WARMUP_EXECUTOR);
    }

    @SuppressWarnings("unchecked")
    private void warmup() {
        try {
            Map<String, Object> seasons = rtaService.getRtaSeasons();
            Object latestSeason = ((List<?>) seasons.getOrDefault("seasons", Collections.emptyList()))
                    .stream().findFirst().orElse(null);
            if (latestSeason == null) return;

            Long seasonId = extractSeasonId(latestSeason);
            if (seasonId == null) return;

            Map<String, Object> ranking = rtaService.getRtaSummonerRanking(WARMUP_TOP_N, 0, seasonId, null);
            List<Map<String, Object>> rows = (List<Map<String, Object>>) ranking.getOrDefault("rankings", Collections.emptyList());

            List<CompletableFuture<Void>> futures = rows.stream()
                    .map(row -> {
                        String wizardId = String.valueOf(row.get("wizard_id"));
                        return CompletableFuture.runAsync(() -> warmupPlayer(wizardId, seasonId), WARMUP_EXECUTOR);
                    })
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("[RTA warmup] 완료 — 상위 {}명 캐시 적재", rows.size());
        } catch (Exception e) {
            log.warn("[RTA warmup] 실패 (무시): {}", e.getMessage());
        }
    }

    private void warmupPlayer(String wizardId, Long seasonId) {
        try {
            rtaService.getRtaPlayerSummary(wizardId, seasonId);
            rtaService.getRtaPlayerMonsterUsage(wizardId, seasonId);
        } catch (Exception e) {
            log.debug("[RTA warmup] wizardId={} 스킵: {}", wizardId, e.getMessage());
        }
    }

    private Long extractSeasonId(Object season) {
        if (season instanceof Map<?, ?> m) {
            Object id = m.get("season_id");
            if (id instanceof Number n) return n.longValue();
            if (id != null) {
                try { return Long.parseLong(String.valueOf(id)); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
