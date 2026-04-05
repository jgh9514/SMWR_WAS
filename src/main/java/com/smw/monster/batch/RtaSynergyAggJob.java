package com.smw.monster.batch;

import java.util.List;

import org.quartz.JobExecutionContext;

import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaSynergyAggService;

/**
 * {@code ranker_rtpvp_replay_list.synergy_agg_status = pending} 인 rid 를 골라
 * {@code rta_synergy_match_fact} · {@code rta_synergy_agg} 에 반영한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 3분마다, bat_id 10003).
 */
public class RtaSynergyAggJob extends BaseBatchJob {

	private static final int BATCH_SIZE = 200;

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaSynergyAggService synergyAggService = applicationContext.getBean(RtaSynergyAggService.class);

		List<Long> rids = rtaMapper.selectPendingSynergyAggRids(BATCH_SIZE);
		if (rids == null || rids.isEmpty()) {
			addLog("시너지 집계 대상 pending rid 없음");
			return;
		}
		addLog("시너지 집계 rid %d건 처리 시작", rids.size());
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
				addLog("rid=%d 시너지 집계 실패 → failed: %s", rid, e.getMessage());
			}
		}
		addLog("시너지 집계 완료 ok=%d fail=%d", ok, fail);
	}

	@Override
	protected String getBatchName() {
		return "RTA 시너지 집계 (솔로·듀오·트리오)";
	}
}
