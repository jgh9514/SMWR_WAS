package com.smw.rta.model;

/**
 * {@code rta_agg_synergy_combo} 증분 UPSERT 용 (시즌×콤보 키 문자열).
 */
public class RtaSynergyAggUpsertRow {

	private long seasonId;
	/** {@code rta_match_participant.rating_id} (해당 진영 소환사 기준) */
	private int ratingId;
	private String comboKey;
	private int comboSize;
	/** 경기 1행당 보통 1. 배치 내 메모리 합산 시 누적 */
	private int matchDelta = 1;
	private int winDelta;

	public RtaSynergyAggUpsertRow() {
	}

	public RtaSynergyAggUpsertRow(long seasonId, String comboKey, int comboSize, int winDelta) {
		this(seasonId, 0, comboKey, comboSize, 1, winDelta);
	}

	public RtaSynergyAggUpsertRow(long seasonId, String comboKey, int comboSize, int matchDelta, int winDelta) {
		this(seasonId, 0, comboKey, comboSize, matchDelta, winDelta);
	}

	public RtaSynergyAggUpsertRow(long seasonId, int ratingId, String comboKey, int comboSize, int matchDelta, int winDelta) {
		this.seasonId = seasonId;
		this.ratingId = ratingId;
		this.comboKey = comboKey;
		this.comboSize = comboSize;
		this.matchDelta = matchDelta;
		this.winDelta = winDelta;
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

	public String getComboKey() {
		return comboKey;
	}

	public void setComboKey(String comboKey) {
		this.comboKey = comboKey;
	}

	public int getComboSize() {
		return comboSize;
	}

	public void setComboSize(int comboSize) {
		this.comboSize = comboSize;
	}

	public int getMatchDelta() {
		return matchDelta;
	}

	public void setMatchDelta(int matchDelta) {
		this.matchDelta = matchDelta;
	}

	public int getWinDelta() {
		return winDelta;
	}

	public void setWinDelta(int winDelta) {
		this.winDelta = winDelta;
	}
}
