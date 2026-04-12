package com.smw.rta.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * RTA 관련 집계 공통 로직 (raw 정규화·스냅샷·시너지·랭킹·몬스터 통계·티어 일별).
 * <p>
 * 배치 Job 여러 개가 동일 규칙을 쓰도록 묶는다.
 */
@Slf4j
@Service
public class RtaBatchAggregationService {

	/** pending rid 한 번에 가져와 스냅샷 반영하는 건수 */
	public static final int SNAPSHOT_BATCH_SIZE = 100000;

	/** 시너지 집계: rid 한 번에 선택하는 건수 */
	public static final int SYNERGY_BATCH_SIZE = 100000;

	/**
	 * v2 레거시 매치 스냅샷 단계 없음 — 별도 집계 테이블/스텝 없이 즉시 완료.
	 */
	@SuppressWarnings("unused")
	public SnapshotDrainResult drainPendingSnapshots(
			RtaMapper rtaMapper,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			int maxRounds,
			boolean evictCachesEachRound) {
		return new SnapshotDrainResult(0, 0, 0, 0, "v2: 레거시 매치 스냅샷 단계 없음");
	}

	/** 소환사 랭킹 스냅샷 agg 테이블 미사용 — API 는 라이브/집계 CTE 집계. */
	@SuppressWarnings("unused")
	public SummonerRankingRebuildResult rebuildSummonerRankingAgg(RtaMapper rtaMapper) {
		return new SummonerRankingRebuildResult(0);
	}

	/**
	 * 원본 스테이징 미적용 건을 정규화 테이블로 반영한다.
	 *
	 * @param maxRounds 루프 상한 (통합 Job은 {@code smw.rta.raw-apply.max-rounds-per-unified-job}, 단발은 1 권장)
	 */
	public RawApplyDrainResult drainReplayRawPending(summonerswarService service, int maxRounds) {
		int orphansDeleted = service.deleteArenaRtaOrphanChildrenGlobal();
		int rounds = 0;
		int totalApplied = 0;
		while (rounds < maxRounds) {
			int applied = service.applyPendingArenaReplayRawFromDb();
			if (applied == 0) {
				break;
			}
			totalApplied += applied;
			rounds++;
		}
		String stopReason;
		if (totalApplied == 0 && orphansDeleted == 0) {
			stopReason = "적용할 raw 없음";
		} else if (rounds >= maxRounds && maxRounds > 1) {
			stopReason = "라운드 상한 도달 (" + maxRounds + ") — 남은 raw 는 다음 실행에서 계속";
		} else {
			stopReason = "완료";
		}
		return new RawApplyDrainResult(orphansDeleted, rounds, totalApplied, stopReason);
	}

	/**
	 * {@code rta_match.synergy_applied_at IS NULL} 인 rid 를 배치 단위로 {@code rta_agg_synergy_combo}에 반영한다. 완료 시 {@code synergy_apply_result='S'}.
	 *
	 * @param pauseMsBetweenRounds 라운드 사이 대기(ms), 0 이면 생략
	 */
	public SynergyDrainResult drainSynergyPending(
			RtaMapper rtaMapper,
			RtaSynergyAggService synergyAggService,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			int maxRounds,
			boolean evictCachesEachRound,
			int pauseMsBetweenRounds) {
		int rounds = 0;
		int totalOk = 0;
		int totalFail = 0;
		String stopReason = null;

		while (rounds < maxRounds) {
			List<Long> rids = rtaMapper.selectPendingSynergyAggRids(batchSize);
			if (rids == null || rids.isEmpty()) {
				stopReason = "pending 없음";
				break;
			}
			// 첫 rid 실패 시 예외로 상위 Quartz Job 이 FAILED 처리되도록 전파
			RtaSynergyAggService.SynergyBatchApplyResult batch = synergyAggService.applySynergyBatch(rids);
			int ok = batch.ok();
			totalOk += ok;
			totalFail += batch.fail();
			rounds++;

			if (evictCachesEachRound && ok > 0) {
				cacheEvictor.evictAllRtaCaches();
			}

			if (pauseMsBetweenRounds > 0 && rounds < maxRounds) {
				sleepQuiet(pauseMsBetweenRounds);
			}
		}
		if (stopReason == null) {
			List<Long> still = rtaMapper.selectPendingSynergyAggRids(1);
			if (still != null && !still.isEmpty() && rounds >= maxRounds) {
				stopReason = "라운드 상한 도달 (" + maxRounds + "), pending 남음 — 다음 스케줄에서 계속";
			} else {
				stopReason = "완료";
			}
		}
		return new SynergyDrainResult(rounds, totalOk, totalFail, stopReason);
	}

	/** {@code rta_agg_monster_unit} 미사용 — 몬스터 통계는 {@code rta_agg_synergy_combo} 경로만 사용. */
	public MonsterStatsRebuildResult rebuildMonsterStatsAgg(RtaMapper rtaMapper) {
		return new MonsterStatsRebuildResult(0, 0);
	}

	/**
	 * 시즌별 {@code rta_agg_tier_daily} 재적재 — 시즌마다 해당 {@code season_id} 행만 삭제 후 INSERT (전역 TRUNCATE 없음).
	 */
	public TierDailyAggRebuildResult rebuildTierAggDaily(RtaMapper rtaMapper) {
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		int totalRows = 0;
		for (Map<String, Object> row : seasons) {
			Long seasonId = pickSeasonId(row);
			if (seasonId == null) {
				continue;
			}
			totalRows += rtaMapper.insertRtaTierAggDailyForSeason(seasonId.longValue());
		}
		return new TierDailyAggRebuildResult(totalRows);
	}

	/**
	 * 랭크 컷 앵커({@code rta_rank_cutoff_anchor_snap}) TRUNCATE 후 라이브와 동일 로직 적재,
	 * 시즌×등급 컷({@code rta_snapshot_rank_cut}) 히스토리 1회 적재.
	 */
	public RankCutSnapshotRebuildResult rebuildRankCutSnapshots(RtaMapper rtaMapper) {
		rtaMapper.deleteAllRtaRankCutoffAnchorSnap();
		int anchorRows = 0;
		String defaultCode = rtaMapper.selectDefaultSeasonCodeForNow();
		if (defaultCode != null && !defaultCode.isEmpty()) {
			Map<String, Object> bounds = rtaMapper.selectRtaSeasonBounds(defaultCode);
			Long sid = bounds != null ? pickSeasonId(bounds) : null;
			if (sid != null) {
				anchorRows = rtaMapper.insertRtaRankCutoffAnchorSnapFromLive(sid.longValue());
			}
		}
		int snapshotRows = rtaMapper.insertRtaSnapshotRankCutForAllSeasons();
		return new RankCutSnapshotRebuildResult(anchorRows, snapshotRows);
	}

	private static Long pickSeasonId(Map<String, Object> row) {
		Object o = row.get("seasonId");
		if (o == null) {
			o = row.get("season_id");
		}
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		return null;
	}

	private static void sleepQuiet(int ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public record SnapshotDrainResult(
			int rounds,
			int totalRidsTouched,
			long totalUpserted,
			long totalMarked,
			String stopReason) {
	}

	public record SummonerRankingRebuildResult(int totalRows) {
	}

	public record RawApplyDrainResult(int orphansDeleted, int rounds, int totalApplied, String stopReason) {
	}

	public record SynergyDrainResult(int rounds, int totalOk, int totalFail, String stopReason) {
	}

	public record MonsterStatsRebuildResult(int metaRows, int pickRows) {
	}

	public record TierDailyAggRebuildResult(int totalRows) {
	}

	public record RankCutSnapshotRebuildResult(int anchorRows, int snapshotRows) {
	}
}
