package com.smw.rta.service;

import java.util.List;

import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

/**
 * RTA 시너지 집계 (경기 1건 → {@code rta_agg_synergy_combo} 증분).
 * {@link #applyOneRid} 는 rid 단위 커밋, {@link #applySynergyBatch} 는 라운드 단일 트랜잭션(통합 배치용).
 */
public interface RtaSynergyAggService {

	record SynergyBatchApplyResult(int ok, int fail) {
	}

	/**
	 * 시너지 미적용 rid 1건에 대해 필드 조합(3마리×2진영=14행 또는 4마리×2진영=28행)을 {@code rta_agg_synergy_combo}에 반영하고 완료 표시한다.
	 *
	 * @throws IllegalStateException 데이터 불충분(2인 미만, 필드 유닛 3~4마리 아님 등)
	 */
	void applyOneRid(long rid);

	/**
	 * 한 라운드의 rid 목록을 하나의 트랜잭션에서 처리한다. 실패한 rid는 {@code markSynergyAggFailed} 후 다음 라운드에서 제외된다.
	 */
	SynergyBatchApplyResult applySynergyBatch(List<Long> rids);

	/**
	 * rid 1건에 대한 시너지 UPSERT 행만 생성 (DB 반영·완료 플래그 없음). Spring Batch Step2 청크에서 묶어 적재할 때 사용한다.
	 */
	List<RtaSynergyAggUpsertRow> buildSynergyRowsForRid(long rid);

	/**
	 * rid 1건에 대한 카운터 매치업 UPSERT 행만 생성 (Spring Batch Step2에서 시너지와 함께 적재).
	 */
	List<RtaCounterMatchupUpsertRow> buildCounterMatchupRowsForRid(long rid);
}
