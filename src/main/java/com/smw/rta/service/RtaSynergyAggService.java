package com.smw.rta.service;

import java.util.List;

import com.smw.rta.model.RtaSynergyAggUpsertRow;

/**
 * RTA 시너지 집계 (경기 1건 → {@code rta_agg_synergy_combo} 증분). 트랜잭션은 구현체에서 rid 단위로 분리한다.
 */
public interface RtaSynergyAggService {

	/**
	 * synergy_agg_status=pending 인 rid 1건에 대해 필드 조합 28개를 {@code rta_agg_synergy_combo}에 반영하고 done 처리한다.
	 *
	 * @throws IllegalStateException 데이터 불충분(2인 미만, 필드 4마리 아님 등)
	 */
	void applyOneRid(long rid);

	/**
	 * rid 1건에 대한 시너지 UPSERT 행만 생성 (DB 반영·완료 플래그 없음). Spring Batch Step2 청크에서 묶어 적재할 때 사용한다.
	 */
	List<RtaSynergyAggUpsertRow> buildSynergyRowsForRid(long rid);
}
