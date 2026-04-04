package com.smw.monster.service;

/**
 * rta-upload: 고아 정리·중복 replay 제외·INSERT 일괄 적용 후 요약 (한 트랜잭션).
 */
public final class ArenaRtaUploadApplyResult {

	private final int orphanRowsRemoved;
	private final int duplicateReplaySkippedCount;

	public ArenaRtaUploadApplyResult(int orphanRowsRemoved, int duplicateReplaySkippedCount) {
		this.orphanRowsRemoved = orphanRowsRemoved;
		this.duplicateReplaySkippedCount = duplicateReplaySkippedCount;
	}

	public int getOrphanRowsRemoved() {
		return orphanRowsRemoved;
	}

	public int getDuplicateReplaySkippedCount() {
		return duplicateReplaySkippedCount;
	}
}
