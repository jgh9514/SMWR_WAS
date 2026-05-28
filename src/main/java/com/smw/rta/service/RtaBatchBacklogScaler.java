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
 * 평소({@code pending <= batchSize})에는 yml 기본값(예: synergy 1라운드, raw 1배치)을 유지하고,
 * 장애·지연으로 backlog 가 쌓이면 한 실행에서 더 많이 처리해 따라잡기(catch-up)한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RtaBatchBacklogScaler {

	private final RtaMapper rtaMapper;
	private final summonerswarMapper swMapper;
	private final RtaBatchProperties batchProperties;
	private final RtaRawApplyProperties rawApplyProperties;

	public RtaBatchBacklogCounts snapshot() {
		long synergy = safeCount(rtaMapper.countPendingSynergyAgg());
		long pickSlot = safeCount(rtaMapper.countPendingPickSlotSnap());
		long summonerRanking = safeCount(rtaMapper.countPendingSummonerRankingReplays());
		long raw = safeCount(swMapper.countRtaReplayRawPending());
		return new RtaBatchBacklogCounts(synergy, raw, pickSlot, summonerRanking);
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

	/**
	 * backlog 가 크면 라운드 간 대기를 줄여 catch-up 속도를 높인다.
	 */
	public int resolveSynergyPauseMs(long synergyPending, int configuredPauseMs, int batchSize) {
		if (!batchProperties.isBacklogScalingEnabled() || configuredPauseMs <= 0) {
			return configuredPauseMs;
		}
		long high = Math.max(batchSize, batchProperties.getBacklogHighWatermark());
		return synergyPending >= high ? 0 : configuredPauseMs;
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
			log.info("[batch-backlog] {} pending={} batchSize={} → maxUnits {} (baseline={}, cap={})",
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

	public record RtaBatchScaledPlan(
			RtaBatchBacklogCounts counts,
			int synergyMaxRounds,
			int rawMaxBatches,
			int pickSlotMaxRounds,
			int synergyPauseMs) {
	}
}
