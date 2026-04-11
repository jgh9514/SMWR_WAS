package com.smw.rta.batch.spring;

/**
 * Spring Batch Step2: 시너지 미집계 replay_id 1건. 행 생성은 writer 에서 IN 조회 후 수행.
 */
public record RtaSynergyBatchItem(long replayId) {
}
