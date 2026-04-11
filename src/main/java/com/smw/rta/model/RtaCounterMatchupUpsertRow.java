package com.smw.rta.model;

/**
 * {@code rta_agg_counter_matchup} 증분 UPSERT — 시즌×주제 소환사 레이팅×필드 유닛×상대 조합 키 단위 승/패.
 */
public class RtaCounterMatchupUpsertRow {

	private long seasonId;
	/** 주제 유닛이 속한 진영 소환사의 {@code rta_match_participant.rating_id} */
	private int ratingId;
	private long subjectUnitId;
	private String opponentComboKey;
	private int opponentComboSize;
	private int winDelta;
	private int loseDelta;

	public RtaCounterMatchupUpsertRow() {
	}

	public RtaCounterMatchupUpsertRow(long seasonId, long subjectUnitId, String opponentComboKey,
			int opponentComboSize, int winDelta, int loseDelta) {
		this(seasonId, 0, subjectUnitId, opponentComboKey, opponentComboSize, winDelta, loseDelta);
	}

	public RtaCounterMatchupUpsertRow(long seasonId, int ratingId, long subjectUnitId, String opponentComboKey,
			int opponentComboSize, int winDelta, int loseDelta) {
		this.seasonId = seasonId;
		this.ratingId = ratingId;
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

	public int getRatingId() {
		return ratingId;
	}

	public void setRatingId(int ratingId) {
		this.ratingId = ratingId;
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
