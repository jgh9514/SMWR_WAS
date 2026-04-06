package com.smw.rta.batch.spring;

import java.util.List;

import com.smw.rta.model.RtaSynergyAggUpsertRow;

/**
 * Spring Batch Step2: rid 1건과 해당 시너지 UPSERT 행 목록.
 */
public record RtaSynergyBatchItem(long replayId, List<RtaSynergyAggUpsertRow> rows) {
}
