package com.smw.monster.batch;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

/**
 * RTA 몬스터 통계(몬스터별·듀오·트리오)를 시즌별로 원천과 동일한 집계 SQL로 {@code rta_monster_stats_*} 에 재적재한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 10분마다, bat_id 10007).
 */
public class RtaMonsterStatsAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		rtaMapper.deleteAllRtaMonsterStatsAgg();
		addLog("rta_monster_stats_* TRUNCATE 완료");

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
				addLog("시즌 경계 없음, 건너뜀: %s", code);
				continue;
			}
			Timestamp start = toTimestamp(bounds.get("start_at"));
			Timestamp end = toTimestamp(bounds.get("end_at"));
			if (start == null || end == null) {
				addLog("시즌 start/end 파싱 실패, 건너뜀: %s", code);
				continue;
			}
			int m = rtaMapper.insertRtaMonsterStatsMetaForSeason(code, start, end);
			int p = rtaMapper.insertRtaMonsterStatsAggForSeason(code, start, end);
			int d = rtaMapper.insertRtaMonsterDuoAggForSeason(code, start, end);
			int t = rtaMapper.insertRtaMonsterTrioAggForSeason(code, start, end);
			metaRows += m;
			pickRows += p;
			duoRows += d;
			trioRows += t;
			addLog("rta_monster_stats season=%s meta=%d pick=%d duo=%d trio=%d", code, m, p, d, t);
		}

		addLog("rta_monster_stats_* 합계: meta=%d, pick=%d, duo=%d, trio=%d", metaRows, pickRows, duoRows, trioRows);
		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (몬스터 통계 집계 갱신)");
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
		return "RTA 몬스터 통계 집계 갱신";
	}
}
