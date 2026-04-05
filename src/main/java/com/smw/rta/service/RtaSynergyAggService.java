package com.smw.rta.service;

/**
 * RTA 시너지 집계 (경기 1건 — fact + 롤업). 트랜잭션은 구현체에서 rid 단위로 분리한다.
 */
public interface RtaSynergyAggService {

	/**
	 * synergy_agg_status=pending 인 rid 1건에 대해 field 조합 28개를 fact·agg 에 반영하고 done 처리한다.
	 *
	 * @throws IllegalStateException 데이터 불충분(2인 미만, 필드 4마리 아님 등)
	 */
	void applyOneRid(long rid);
}
