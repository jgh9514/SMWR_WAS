package com.smw.rta.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * RTA 관련 집계 공통 로직 (raw 정규화·시너지·랭킹·몬스터 통계·티어 일별).
 * <p>
 * 배치 Job 여러 개가 동일 규칙을 쓰도록 묶는다.
 */
@Slf4j
@Service
public class RtaBatchAggregationService {

	public SummonerRankingRebuildResult rebuildSummonerRankingAgg(RtaMapper rtaMapper) {
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		for (Map<String, Object> row : seasons) {
			Long seasonId = pickSeasonId(row);
			if (seasonId == null) {
				continue;
			}
			rtaMapper.deleteRtaSummonerRankingSnapBySeason(seasonId.longValue());
			rtaMapper.insertRtaSummonerRankingSnapForSeason(seasonId.longValue());
		}
		return new SummonerRankingRebuildResult((int) safeCount(rtaMapper.countRtaSummonerRankingSnapRows()));
	}

	/**
	 * 원본 스테이징 미적용 건을 정규화 테이블로 반영한다.
	 * {@link summonerswarService#applyPendingArenaReplayRawFromDb()} 는 {@code max-rows-per-run} 행을
	 * 1회만 조회·처리하고 종료한다. 잔여 행은 다음 스케줄에서 처리된다. 고아 행 삭제는 통합 Job 에서 하지 않는다.
	 */
	public RawApplyDrainResult drainReplayRawPending(summonerswarService service) {
		int totalApplied = service.applyPendingArenaReplayRawFromDb();
		String stopReason = totalApplied == 0 ? "적용할 raw 없음" : "완료";
		return new RawApplyDrainResult(totalApplied, stopReason);
	}

	/**
	 * {@code rta_match.synergy_applied_at IS NULL} 인 rid 를 배치 단위로
	 * {@code rta_agg_synergy_solo/duo/trio} 및 {@code rta_agg_counter_solo/duo/trio}에 반영한다.
	 * 완료 시 {@code synergy_apply_result='S'}. pending 이 모두 소진될 때까지 반복한다 (라운드 상한 없음).
	 *
	 * @param pauseMsBetweenRounds 라운드 사이 대기(ms), 0 이면 생략
	 */
	public SynergyDrainResult drainSynergyPending(
			RtaMapper rtaMapper,
			RtaSynergyAggService synergyAggService,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			boolean evictCachesEachRound,
			int pauseMsBetweenRounds) {
		int rounds = 0;
		int totalOk = 0;
		int totalFail = 0;

		// idx_rta_match_synergy_pending(partial index) 강제 사용 — Seq Scan 방지
		rtaMapper.hintBatchDisableSeqScan();
		try {
		while (true) {
			List<Long> rids = rtaMapper.selectPendingSynergyAggRids(batchSize);
			if (rids == null || rids.isEmpty()) {
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

			if (pauseMsBetweenRounds > 0) {
				sleepQuiet(pauseMsBetweenRounds);
			}
		}
		} finally {
			// 세션 플래너 설정 복원 — 예외 발생 시에도 반드시 복원
			rtaMapper.hintBatchEnableSeqScan();
		}
		return new SynergyDrainResult(rounds, totalOk, totalFail, "완료");
	}

	/** no-op — 몬스터 통계는 {@code rta_agg_synergy_solo/duo/trio} 집계 테이블에서 직접 조회. */
	public MonsterStatsRebuildResult rebuildMonsterStatsAgg(RtaMapper rtaMapper) {
		return new MonsterStatsRebuildResult(0, 0);
	}

	/**
	 * 시즌별 {@code rta_agg_tier_daily} 전량 재적재.
	 * <p>
	 * 당일만 MERGE하는 증분이 아니라, 시즌마다 해당 {@code season_id} 행을 모두 DELETE 한 뒤
	 * {@code rta_season} 기준(서울) 일자 구간에 대해 누적 집계를 처음부터 다시 넣는다.
	 * 배치가 중간에 실패했더라도 다음 실행에서 같은 방식으로 전 일자가 일관되게 복구된다.
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
	 * 시즌×티어 총 경기 수({@code rta_agg_season_rating_match_total})를 먼저 재집계한 뒤,
	 * 랭크 컷 앵커({@code rta_rank_cutoff_anchor_snap})를 DELETE 후 라이브와 동일 로직으로 재적재,
	 * 시즌×등급 컷({@code rta_snapshot_rank_cut}) 히스토리 1회 적재.
	 */
	public RankCutSnapshotRebuildResult rebuildRankCutSnapshots(RtaMapper rtaMapper) {
		rtaMapper.rebuildRtaSeasonRatingMatchTotal();
		rtaMapper.deleteAllRtaRankCutoffAnchorSnap();
		Long defaultSid = rtaMapper.selectDefaultSeasonIdForNow();
		if (defaultSid != null) {
			rtaMapper.insertRtaRankCutoffAnchorSnapFromLive(defaultSid.longValue());
		}
		rtaMapper.insertRtaSnapshotRankCutForAllSeasons();
		long matchTotalRows = safeCount(rtaMapper.countRtaSeasonRatingMatchTotalRows());
		long anchorRows = safeCount(rtaMapper.countRtaRankCutoffAnchorSnapRows());
		long snapshotRows = safeCount(rtaMapper.countRtaSnapshotRankCutAtLatestSnapshot());
		return new RankCutSnapshotRebuildResult(matchTotalRows, anchorRows, snapshotRows);
	}

	private static long safeCount(Long n) {
		return n != null && n >= 0 ? n : 0L;
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

	public record SummonerRankingRebuildResult(int totalRows) {
	}

	public record RawApplyDrainResult(int totalApplied, String stopReason) {
	}

	public record SynergyDrainResult(int rounds, int totalOk, int totalFail, String stopReason) {
	}

	public record MonsterStatsRebuildResult(int metaRows, int pickRows) {
	}

	public record TierDailyAggRebuildResult(int totalRows) {
	}

	public record RankCutSnapshotRebuildResult(long matchTotalRows, long anchorRows, long snapshotRows) {
	}
}
