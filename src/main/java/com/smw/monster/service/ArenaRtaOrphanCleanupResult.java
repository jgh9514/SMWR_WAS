package com.smw.monster.service;

/**
 * {@link summonerswarService#deleteArenaRtaOrphanChildrenGlobal()} 실행 요약.
 */
public record ArenaRtaOrphanCleanupResult(
		int unitsDeleted,
		int participantsDeleted,
		int orphanReplayIdBatches,
		String checkMode,
		Long floorReplayId) {

	public ArenaRtaOrphanCleanupResult(int unitsDeleted, int participantsDeleted, int orphanReplayIdBatches) {
		this(unitsDeleted, participantsDeleted, orphanReplayIdBatches, null, null);
	}

	public int totalDeleted() {
		return unitsDeleted + participantsDeleted;
	}
}
