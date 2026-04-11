package com.smw.rta.service;

import java.util.List;
import java.util.Map;

import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

/**
 * RTA 시너지 집계 (경기 1건 → {@code rta_agg_synergy_combo} 증분).
 * {@link #applyOneRid} 는 rid 단위 커밋. {@link #applySynergyBatch} 는 라운드 내 rid 를 순회하며 (시즌×콤보)·카운터 키를 맵에 누적 합산한 뒤 bulk UPSERT·일괄 완료 처리한다(경기당 수백 행을 전부 List 로 쌓지 않음).
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
	 * 한 라운드 rid 목록: 행을 모은 뒤 (시즌×콤보 키)·카운터 키로 메모리 합산 → 청크 UPSERT → {@code markSynergyAggDoneForRids} 일괄.
	 * 첫 파싱 실패 시 해당 rid 만 failed 표기 후 예외(동일 라운드는 아직 DB 미반영).
	 */
	SynergyBatchApplyResult applySynergyBatch(List<Long> rids);

	/**
	 * {@code rta_match.synergy_applied_at}·{@code synergy_apply_result='S'} 일괄 갱신. {@code replay_id = ANY(bigint[])} 단일 호출.
	 *
	 * @return 갱신된 행 수 합계
	 */
	int markSynergyAggDoneForRidsBatched(List<Long> rids);

	/** 동일 (season_id, combo_unit_key) 행을 합쳐 match_cnt·win_cnt 증분을 누적한다. */
	List<RtaSynergyAggUpsertRow> mergeSynergyAggRows(List<RtaSynergyAggUpsertRow> rows);

	/** 동일 (season_id, subject_unit_id, opponent_combo_key) 행을 합친다. */
	List<RtaCounterMatchupUpsertRow> mergeCounterMatchupRows(List<RtaCounterMatchupUpsertRow> rows);

	/**
	 * rta_match·rta_match_unit_pick·rta_match_participant(등급) 을 rid 목록 기준 IN 조회로 채운다.
	 * {@link #buildRowsFromLookup} 전에 한 번 호출.
	 */
	void prefetchSynergyLookup(List<Long> rids, Map<Long, Map<String, Object>> replayByRid,
			Map<Long, List<Map<String, Object>>> unitsByRid,
			Map<Long, List<Map<String, Object>>> ratingsByRid);

	/**
	 * {@link #prefetchSynergyLookup} 으로 채운 맵에서 경기 1건의 시너지·카운터 행을 한 번에 생성 (파싱·검증 1회).
	 */
	SynergyRidBuildResult buildRowsFromLookup(long rid, Map<Long, Map<String, Object>> replayByRid,
			Map<Long, List<Map<String, Object>>> unitsByRid,
			Map<Long, List<Map<String, Object>>> ratingsByRid);

	/** {@link #buildRowsFromLookup} 반환용 */
	record SynergyRidBuildResult(List<RtaSynergyAggUpsertRow> synergyRows,
			List<RtaCounterMatchupUpsertRow> counterRows) {
	}

	/**
	 * rid 1건에 대한 시너지 UPSERT 행만 생성 (DB 2회 조회). NDJSON 외 단건 테스트·호환용.
	 */
	List<RtaSynergyAggUpsertRow> buildSynergyRowsForRid(long rid);

	/**
	 * rid 1건에 대한 카운터 매치업 UPSERT 행만 생성 (DB 2회 조회).
	 */
	List<RtaCounterMatchupUpsertRow> buildCounterMatchupRowsForRid(long rid);
}
