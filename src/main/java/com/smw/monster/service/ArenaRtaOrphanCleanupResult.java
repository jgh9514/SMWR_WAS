package com.smw.monster.service;

/**
 * {@link summonerswarService#deleteArenaRtaOrphanChildrenGlobal()} 실행 요약.
 */
public record ArenaRtaOrphanCleanupResult(
		int unitsDeleted,
		int participantsDeleted,
		int orphanReplayIdBatches) {

	public int totalDeleted() {
		return unitsDeleted + participantsDeleted;
	}
}
