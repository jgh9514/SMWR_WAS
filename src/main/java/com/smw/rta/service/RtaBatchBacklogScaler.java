package com.smw.rta.service;

import org.springframework.stereotype.Service;

import com.smw.monster.mapper.summonerswarMapper;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.mapper.RtaMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RTA 배치 미처리(backlog) 규모에 따라 Job 당 라운드·배치 상한을 동적으로 조정한다.
 * <p>
 * 시너지 pending 50만+ 일 때 {@code COUNT(*)} 는 partial index 전체 스캔으로 수십 초~수 분 —
 * 배치 Job 핫패스에서는 {@link RtaMapper#existsPendingSynergyAgg()} 만 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RtaBatchBacklogScaler {

	private final RtaMapper rtaMapper;
	private final summonerswarMapper swMapper;
	private final RtaBatchProperties batchProperties;
	private final RtaRawApplyProperties rawApplyProperties;

	private volatile long synergyPendingCached = -1L;
	private volatile long synergyPendingCachedAtMs = 0L;

	/** 관리 API·모니터링 — synergy COUNT 는 TTL 캐시(기본 5분). */
	public RtaBatchBacklogCounts snapshot() {
		return new RtaBatchBacklogCounts(
				synergyPendingCachedOrCount(),
				safeCount(swMapper.countRtaReplayRawPending()),
				safeCount(rtaMapper.countPendingPickSlotSnap()),
				safeCount(rtaMapper.countPendingSummonerRankingReplays()));
	}

	/**
	 * {@link com.smw.monster.batch.RtaSynergyOnlyAggJob} — COUNT 없이 라운드·pause 결정(기본).
	 */
	public SynergyJobPlan planSynergyDrain(int batchSize, int configuredMaxRounds, int configuredPauseMs) {
		boolean hasPending = Boolean.TRUE.equals(rtaMapper.existsPendingSynergyAgg());
		Long exactPending = null;
		int maxRounds;
		int pauseMs;

		if (batchProperties.isExactPendingCountInBatchJobs()) {
			exactPending = safeCount(rtaMapper.countPendingSynergyAgg());
			maxRounds = resolveSynergyMaxRounds(exactPending, batchSize, configuredMaxRounds);
			pauseMs = resolveSynergyPauseMs(exactPending, configuredPauseMs, batchSize);
		} else {
			maxRounds = resolveSynergyMaxRoundsWithoutCount(hasPending, batchSize, configuredMaxRounds);
			pauseMs = resolveSynergyPauseMsWithoutCount(hasPending, configuredPauseMs, batchSize);
		}
		return new SynergyJobPlan(hasPending, maxRounds, pauseMs, exactPending);
	}

	public long synergyPendingCachedOrCount() {
		int ttlSec = batchProperties.getPendingCountCacheTtlSeconds();
		long now = System.currentTimeMillis();
		if (ttlSec > 0 && synergyPendingCached >= 0
				&& now - synergyPendingCachedAtMs < ttlSec * 1000L) {
			return synergyPendingCached;
		}
		long counted = safeCount(rtaMapper.countPendingSynergyAgg());
		synergyPendingCached = counted;
		synergyPendingCachedAtMs = now;
		return counted;
	}

	/** 시너지 drain 후 API 캐시 무효화(선택). */
	public void invalidateSynergyPendingCache() {
		synergyPendingCached = -1L;
		synergyPendingCachedAtMs = 0L;
	}

	/**
	 * 시너지 drain 라운드 상한. {@code configuredMaxRounds <= 0} 이면 무제한(기존 drain-until-empty).
	 */
	public int resolveSynergyMaxRounds(long synergyPending, int batchSize, int configuredMaxRounds) {
		return resolveMaxUnits(
				synergyPending,
				batchSize,
				configuredMaxRounds,
				batchProperties.getSynergyMaxRoundsCap(),
				"synergy");
	}

	int resolveSynergyMaxRoundsWithoutCount(boolean hasPending, int batchSize, int configuredMaxRounds) {
		if (configuredMaxRounds <= 0) {
			return 0;
		}
		int baseline = Math.max(1, configuredMaxRounds);
		if (!batchProperties.isBacklogScalingEnabled()) {
			return baseline;
		}
		if (!hasPending) {
			return baseline;
		}
		int safeCap = Math.max(baseline, batchProperties.getSynergyMaxRoundsCap());
		if (safeCap > baseline) {
			log.debug("[batch-backlog] synergy pending EXISTS — maxUnits {} (baseline={}, cap={}, COUNT 생략)",
					safeCap, baseline, safeCap);
		}
		return safeCap;
	}

	public int resolveRawMaxBatches(long rawPending, int rowsPerBatch, int configuredMaxBatches) {
		return resolveMaxUnits(
				rawPending,
				rowsPerBatch,
				configuredMaxBatches,
				rawApplyProperties.getMaxBatchesCap(),
				"raw");
	}

	public int resolvePickSlotMaxRounds(long pickSlotPending, int chunkSize, int configuredMaxRounds) {
		int defaultRounds = configuredMaxRounds <= 0 ? 0 : Math.max(1, configuredMaxRounds);
		return resolveMaxUnits(
				pickSlotPending,
				chunkSize,
				defaultRounds,
				batchProperties.getPickSlotMaxRoundsCap(),
				"pick-slot");
	}

	public int resolveSynergyPauseMs(long synergyPending, int configuredPauseMs, int batchSize) {
		if (!batchProperties.isBacklogScalingEnabled() || configuredPauseMs <= 0) {
			return configuredPauseMs;
		}
		long high = Math.max(batchSize, batchProperties.getBacklogHighWatermark());
		return synergyPending >= high ? 0 : configuredPauseMs;
	}

	int resolveSynergyPauseMsWithoutCount(boolean hasPending, int configuredPauseMs, int batchSize) {
		if (!batchProperties.isBacklogScalingEnabled() || configuredPauseMs <= 0) {
			return configuredPauseMs;
		}
		if (!hasPending) {
			return configuredPauseMs;
		}
		long high = Math.max(batchSize, batchProperties.getBacklogHighWatermark());
		return high > 0 ? 0 : configuredPauseMs;
	}

	private int resolveMaxUnits(
			long pending,
			int batchSize,
			int configuredDefault,
			int cap,
			String label) {
		int safeBatch = Math.max(1, batchSize);
		if (configuredDefault <= 0) {
			return 0;
		}
		int baseline = Math.max(1, configuredDefault);
		if (!batchProperties.isBacklogScalingEnabled()) {
			return baseline;
		}
		if (pending <= safeBatch) {
			return baseline;
		}
		long needed = (pending + safeBatch - 1L) / safeBatch;
		int safeCap = Math.max(baseline, cap);
		int scaled = (int) Math.min(safeCap, Math.max(baseline, needed));
		if (scaled > baseline) {
			log.debug("[batch-backlog] {} pending={} batchSize={} → maxUnits {} (baseline={}, cap={})",
					label, pending, safeBatch, scaled, baseline, safeCap);
		}
		return scaled;
	}

	private static long safeCount(Long value) {
		return value != null && value > 0 ? value : 0L;
	}

	public record RtaBatchBacklogCounts(
			long synergyPending,
			long rawPending,
			long pickSlotPending,
			long summonerRankingPending) {
	}

	public record SynergyJobPlan(
			boolean hasPending,
			int maxRounds,
			int pauseMs,
			/** {@code exactPendingCountInBatchJobs=true} 일 때만 채움. */
			Long exactPendingCount) {
	}

	public record RtaBatchScaledPlan(
			RtaBatchBacklogCounts counts,
			int synergyMaxRounds,
			int rawMaxBatches,
			int pickSlotMaxRounds,
			int synergyPauseMs) {
	}
}
