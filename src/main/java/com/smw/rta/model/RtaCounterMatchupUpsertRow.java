package com.smw.rta.model;

/**
 * {@code rta_agg_counter_matchup} 증분 UPSERT — 몬스터(필드 유닛) 기준 상대 팀 듀오·트리오에 대한 승/패.
 */
public class RtaCounterMatchupUpsertRow {

	private long seasonId;
	private long subjectUnitId;
	private String opponentComboKey;
	private int opponentComboSize;
	private int winDelta;
	private int loseDelta;

	public RtaCounterMatchupUpsertRow() {
	}

	public RtaCounterMatchupUpsertRow(long seasonId, long subjectUnitId, String opponentComboKey,
			int opponentComboSize, int winDelta, int loseDelta) {
		this.seasonId = seasonId;
		this.subjectUnitId = subjectUnitId;
		this.opponentComboKey = opponentComboKey;
		this.opponentComboSize = opponentComboSize;
		this.winDelta = winDelta;
		this.loseDelta = loseDelta;
	}

	public long getSeasonId() {
		return seasonId;
	}

	public void setSeasonId(long seasonId) {
		this.seasonId = seasonId;
	}

	public long getSubjectUnitId() {
		return subjectUnitId;
	}

	public void setSubjectUnitId(long subjectUnitId) {
		this.subjectUnitId = subjectUnitId;
	}

	public String getOpponentComboKey() {
		return opponentComboKey;
	}

	public void setOpponentComboKey(String opponentComboKey) {
		this.opponentComboKey = opponentComboKey;
	}

	public int getOpponentComboSize() {
		return opponentComboSize;
	}

	public void setOpponentComboSize(int opponentComboSize) {
		this.opponentComboSize = opponentComboSize;
	}

	public int getWinDelta() {
		return winDelta;
	}

	public void setWinDelta(int winDelta) {
		this.winDelta = winDelta;
	}

	public int getLoseDelta() {
		return loseDelta;
	}

	public void setLoseDelta(int loseDelta) {
		this.loseDelta = loseDelta;
	}
}
