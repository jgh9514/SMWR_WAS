package com.smw.monster.service;

/**
 * rta-upload 적재 모드.
 * <ul>
 *   <li>{@link #FULL} — 원본 raw + 정규화 테이블 + raw 적용 완료 표시 (API/일반 업로드)</li>
 *   <li>{@link #RAW_ONLY} — 원본 JSON 스테이징만 (Exporter watch-directory → 배치 정규화 전)</li>
 *   <li>{@link #NORMALIZED_ONLY} — 정규화 + raw 적용 완료 (배치가 raw 에서 꺼내 적용)</li>
 * </ul>
 */
public enum ArenaRtaPersistMode {
	FULL,
	RAW_ONLY,
	NORMALIZED_ONLY
}
