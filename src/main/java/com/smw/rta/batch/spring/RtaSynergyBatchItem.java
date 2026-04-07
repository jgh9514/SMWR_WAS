package com.smw.rta.batch.spring;

import java.util.List;

import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

/**
 * Spring Batch Step2: rid 1건과 해당 시너지·카운터 매치업 UPSERT 행 목록.
 */
public record RtaSynergyBatchItem(long replayId, List<RtaSynergyAggUpsertRow> rows,
		List<RtaCounterMatchupUpsertRow> counterRows) {
}
