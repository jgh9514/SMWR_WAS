package com.smw.rta.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * RTA 관련 집계 공통 로직 (raw 정규화·스냅샷·시너지·랭킹·티어·몬스터 통계).
 * <p>
 * 배치 Job 여러 개가 동일 규칙을 쓰도록 묶는다.
 */
@Slf4j
@Service
public class RtaBatchAggregationService {

	/** pending rid 한 번에 가져와 스냅샷 반영하는 건수 */
	public static final int SNAPSHOT_BATCH_SIZE = 3000;

	/** 한 배치 실행에서 스냅샷 루프 최대 횟수 (무한 루프·장시간 점유 방지) */
	public static final int MAX_SNAPSHOT_ROUNDS_PER_JOB = 100_000;

	/** raw 정규화 루프 상한 (한 라운드 = apply 한 번) */
	public static final int MAX_RAW_APPLY_ROUNDS_PER_JOB = 100_000;

	/** 시너지 집계: rid 한 번에 선택하는 건수 */
	public static final int SYNERGY_BATCH_SIZE = 200;

	public static final int MAX_SYNERGY_ROUNDS_PER_JOB = 100_000;

	/**
	 * pending 이 없어질 때까지(또는 상한·정체 감지 시) 스냅샷을 반복 적재한다.
	 *
	 * @param batchSize   {@link #SNAPSHOT_BATCH_SIZE} 권장
	 * @param maxRounds   라운드 상한 ({@link #MAX_SNAPSHOT_ROUNDS_PER_JOB} 권장)
	 * @param evictCaches 라운드마다 캐시 무효화 여부. 통합 배치 끝에서 한 번만 하려면 false
	 */
	public SnapshotDrainResult drainPendingSnapshots(
			RtaMapper rtaMapper,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			int maxRounds,
			boolean evictCachesEachRound) {
		int rounds = 0;
		int totalRidsTouched = 0;
		long totalUpserted = 0;
		long totalMarked = 0;
		int consecutiveNoProgress = 0;
		String stopReason = null;

		while (rounds < maxRounds) {
			List<Long> rids = rtaMapper.selectPendingRtaAggRids(batchSize);
			if (rids == null || rids.isEmpty()) {
				stopReason = "pending 없음";
				break;
			}
			totalRidsTouched += rids.size();
			int upserted = rtaMapper.upsertRtaMatchSnapshotsForRids(rids);
			int marked = rtaMapper.markRtaAggDoneForRidsWithSnapshot(rids);
			totalUpserted += upserted;
			totalMarked += marked;
			rounds++;

			if (evictCachesEachRound && (upserted > 0 || marked > 0)) {
				cacheEvictor.evictAllRtaCaches();
			}

			if (upserted == 0 && marked == 0) {
				consecutiveNoProgress++;
				if (consecutiveNoProgress >= 3) {
					stopReason = "스냅샷 반영 0건 3회 연속 — pending·원천 데이터 점검 필요";
					log.warn("[rta-batch] {}", stopReason);
					break;
				}
			} else {
				consecutiveNoProgress = 0;
			}
		}
		if (stopReason == null) {
			List<Long> stillPending = rtaMapper.selectPendingRtaAggRids(1);
			if (stillPending != null && !stillPending.isEmpty() && rounds >= maxRounds) {
				stopReason = "라운드 상한 도달 (" + maxRounds + "), pending 남음 — 다음 스케줄에서 계속";
			} else {
				stopReason = "완료";
			}
		}
		return new SnapshotDrainResult(rounds, totalRidsTouched, totalUpserted, totalMarked, stopReason);
	}

	/** 소환사 랭킹 집계 테이블 전체 재적재 (시즌별 INSERT) */
	public SummonerRankingRebuildResult rebuildSummonerRankingAgg(RtaMapper rtaMapper) {
		rtaMapper.deleteAllRtaSummonerRankingAgg();
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		int totalRows = 0;
		for (Map<String, Object> row : seasons) {
			String code = pickSeasonCode(row);
			if (code == null || code.isEmpty()) {
				continue;
			}
			Map<String, Object> bounds = rtaMapper.selectRtaSeasonBounds(code);
			if (bounds == null || bounds.isEmpty()) {
				log.warn("[rta-batch] 시즌 경계 없음, 건너뜀: {}", code);
				continue;
			}
			Timestamp start = toTimestamp(bounds.get("start_at"));
			Timestamp end = toTimestamp(bounds.get("end_at"));
			if (start == null || end == null) {
				log.warn("[rta-batch] 시즌 start/end 파싱 실패, 건너뜀: {}", code);
				continue;
			}
			int n = rtaMapper.insertRtaSummonerRankingAggForSeason(code, start, end);
			totalRows += n;
		}
		return new SummonerRankingRebuildResult(totalRows);
	}

	/**
	 * ranker_rtpvp_replay_raw 미적용 건을 정규화 테이블로 반영한다.
	 *
	 * @param maxRounds 통합 배치는 {@link #MAX_RAW_APPLY_ROUNDS_PER_JOB}, 단발 배치는 1
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
	 * synergy_agg_status = pending 인 rid 를 배치 단위로 시너지 fact·롤업에 반영한다.
	 */
	public SynergyDrainResult drainSynergyPending(
			RtaMapper rtaMapper,
			RtaSynergyAggService synergyAggService,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			int maxRounds,
			boolean evictCachesEachRound) {
		int rounds = 0;
		int totalOk = 0;
		int totalFail = 0;
		int consecutiveAllFailed = 0;
		String stopReason = null;

		while (rounds < maxRounds) {
			List<Long> rids = rtaMapper.selectPendingSynergyAggRids(batchSize);
			if (rids == null || rids.isEmpty()) {
				stopReason = "pending 없음";
				break;
			}
			int ok = 0;
			int fail = 0;
			for (Long rid : rids) {
				if (rid == null) {
					continue;
				}
				try {
					synergyAggService.applyOneRid(rid);
					ok++;
				} catch (Exception e) {
					fail++;
					rtaMapper.markSynergyAggFailed(rid);
					log.warn("[rta-batch] rid={} 시너지 집계 실패: {}", rid, e.getMessage());
				}
			}
			totalOk += ok;
			totalFail += fail;
			rounds++;

			if (evictCachesEachRound && ok > 0) {
				cacheEvictor.evictAllRtaCaches();
			}

			if (ok == 0 && fail == rids.size()) {
				consecutiveAllFailed++;
				if (consecutiveAllFailed >= 3) {
					stopReason = "시너지 rid 전부 실패 3회 연속 — 원천·스냅샷 점검 필요";
					log.warn("[rta-batch] {}", stopReason);
					break;
				}
			} else {
				consecutiveAllFailed = 0;
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

	/** 티어 일별 분포만 (대시보드 차트용). 랭크 컷은 API 라이브 조회 + 1시간 캐시. */
	public int rebuildTierDistributionDailyAgg(RtaMapper rtaMapper) {
		rtaMapper.deleteAllRtaTierDistributionDailyAgg();
		return rtaMapper.insertRtaTierDistributionDailyAggFromLive();
	}

	public MonsterStatsRebuildResult rebuildMonsterStatsAgg(RtaMapper rtaMapper) {
		rtaMapper.deleteAllRtaMonsterStatsAgg();
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		int metaRows = 0;
		int pickRows = 0;
		int duoRows = 0;
		int trioRows = 0;
		for (Map<String, Object> row : seasons) {
			String code = pickSeasonCode(row);
			if (code == null || code.isEmpty()) {
				continue;
			}
			Map<String, Object> bounds = rtaMapper.selectRtaSeasonBounds(code);
			if (bounds == null || bounds.isEmpty()) {
				log.warn("[rta-batch] 몬스터 통계 시즌 경계 없음, 건너뜀: {}", code);
				continue;
			}
			Timestamp start = toTimestamp(bounds.get("start_at"));
			Timestamp end = toTimestamp(bounds.get("end_at"));
			if (start == null || end == null) {
				log.warn("[rta-batch] 몬스터 통계 시즌 start/end 파싱 실패, 건너뜀: {}", code);
				continue;
			}
			metaRows += rtaMapper.insertRtaMonsterStatsMetaForSeason(code, start, end);
			pickRows += rtaMapper.insertRtaMonsterStatsAggForSeason(code, start, end);
			duoRows += rtaMapper.insertRtaMonsterDuoAggForSeason(code, start, end);
			trioRows += rtaMapper.insertRtaMonsterTrioAggForSeason(code, start, end);
		}
		return new MonsterStatsRebuildResult(metaRows, pickRows, duoRows, trioRows);
	}

	private static String pickSeasonCode(Map<String, Object> row) {
		Object sc = row.get("seasonCode");
		if (sc == null) {
			sc = row.get("season_code");
		}
		return sc != null ? String.valueOf(sc).trim() : "";
	}

	private static Timestamp toTimestamp(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Timestamp) {
			return (Timestamp) o;
		}
		if (o instanceof java.util.Date) {
			return new Timestamp(((java.util.Date) o).getTime());
		}
		return null;
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

	public record MonsterStatsRebuildResult(int metaRows, int pickRows, int duoRows, int trioRows) {
	}
}
