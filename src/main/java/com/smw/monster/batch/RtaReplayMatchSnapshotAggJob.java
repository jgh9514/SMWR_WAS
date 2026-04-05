package com.smw.monster.batch;

import java.util.List;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

/**
 * {@code ranker_rtpvp_replay_list.rta_agg_status = pending} 인 rid 를 골라
 * {@code rta_replay_match_snapshot} 에 평탄화 적재하고, 성공 건만 {@code done} 으로 표시한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 2분마다, bat_id 10002).
 */
public class RtaReplayMatchSnapshotAggJob extends BaseBatchJob {

	private static final int BATCH_SIZE = 500;

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		List<Long> rids = rtaMapper.selectPendingRtaAggRids(BATCH_SIZE);
		if (rids == null || rids.isEmpty()) {
			addLog("스냅샷 대상 pending rid 없음");
			return;
		}
		addLog("pending rid %d건 스냅샷 적재 시작", rids.size());
		int upserted = rtaMapper.upsertRtaMatchSnapshotsForRids(rids);
		addLog("upsert 반영 행수: %d", upserted);
		int marked = rtaMapper.markRtaAggDoneForRidsWithSnapshot(rids);
		addLog("rta_agg_status=done 갱신: %d건", marked);
		if (upserted > 0 || marked > 0) {
			rtaCacheEvictor.evictAllRtaCaches();
			addLog("RTA 조회 캐시 무효화");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 매치 스냅샷 집계 (조회용 평탄화)";
	}
}
