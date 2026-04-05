package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.account.mapper.AccountSummaryMapper;

/**
 * SWEX 임포트 기준 소환사 보유 몬스터를 {@code user_monster_owned_agg} 에 재적재한다.
 * 소환사(wizard_id)마다 {@code import_id} 가 가장 큰 임포트를 최신으로 보고, 그 임포트의
 * {@code swex_monster} 를 master_id 당 마리 수로 집계한다. RTA 시즌과 무관.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 15분마다, bat_id 10006).
 */
public class UserMonsterOwnedAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		AccountSummaryMapper mapper = applicationContext.getBean(AccountSummaryMapper.class);
		mapper.deleteAllUserMonsterOwnedAgg();
		addLog("user_monster_owned_agg 전체 삭제");
		int n = mapper.insertUserMonsterOwnedAggFromSwex();
		addLog("user_monster_owned_agg 적재: %d행", n);
	}

	@Override
	protected String getBatchName() {
		return "사용자 보유 몬스터 집계 (SWEX)";
	}
}
