package com.smw.monster.batch;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

/**
 * 소환사 랭킹 스냅샷을 시즌별로 원천과 동일한 집계 SQL로 재적재한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 5분마다, bat_id 10005).
 */
public class RtaSummonerRankingAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		rtaMapper.deleteAllRtaSummonerRankingAgg();
		addLog("rta_summoner_ranking_agg 전체 삭제");

		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		int totalRows = 0;
		for (Map<String, Object> row : seasons) {
			String code = pickSeasonCode(row);
			if (code == null || code.isEmpty()) {
				continue;
			}
			Map<String, Object> bounds = rtaMapper.selectRtaSeasonBounds(code);
			if (bounds == null || bounds.isEmpty()) {
				addLog("시즌 경계 없음, 건너뜀: %s", code);
				continue;
			}
			Timestamp start = toTimestamp(bounds.get("start_at"));
			Timestamp end = toTimestamp(bounds.get("end_at"));
			if (start == null || end == null) {
				addLog("시즌 start/end 파싱 실패, 건너뜀: %s", code);
				continue;
			}
			int n = rtaMapper.insertRtaSummonerRankingAggForSeason(code, start, end);
			totalRows += n;
			addLog("rta_summoner_ranking_agg 적재: season=%s rows=%d", code, n);
		}

		addLog("rta_summoner_ranking_agg 합계: %d행", totalRows);
		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (소환사 랭킹 집계 갱신)");
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

	@Override
	protected String getBatchName() {
		return "RTA 소환사 랭킹 집계 갱신";
	}
}
