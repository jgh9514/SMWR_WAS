package com.smw.rta.model;

/**
 * {@code rta_agg_synergy_solo.ban_cnt} 증분 (시즌×티어×콤보 단위).
 */
public class RtaSynergyBanDeltaRow {

	private long seasonId;
	private int ratingId;
	private String comboUnitKey;
	private long delta;

	public RtaSynergyBanDeltaRow() {
	}

	public RtaSynergyBanDeltaRow(long seasonId, int ratingId, String comboUnitKey, long delta) {
		this.seasonId = seasonId;
		this.ratingId = ratingId;
		this.comboUnitKey = comboUnitKey;
		this.delta = delta;
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

	public String getComboUnitKey() {
		return comboUnitKey;
	}

	public void setComboUnitKey(String comboUnitKey) {
		this.comboUnitKey = comboUnitKey;
	}

	public long getDelta() {
		return delta;
	}

	public void setDelta(long delta) {
		this.delta = delta;
	}
}
