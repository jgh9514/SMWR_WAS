package com.smw.rta.model;

/**
 * {@code rta_agg_synergy_combo} 증분 UPSERT 용 (시즌×등급×콤보 키 문자열).
 */
public class RtaSynergyAggUpsertRow {

	private long seasonId;
	private String comboKey;
	private int comboSize;
	private int winDelta;

	public RtaSynergyAggUpsertRow() {
	}

	public RtaSynergyAggUpsertRow(long seasonId, String comboKey, int comboSize, int winDelta) {
		this.seasonId = seasonId;
		this.comboKey = comboKey;
		this.comboSize = comboSize;
		this.winDelta = winDelta;
	}

	public long getSeasonId() {
		return seasonId;
	}

	public void setSeasonId(long seasonId) {
		this.seasonId = seasonId;
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

	public int getWinDelta() {
		return winDelta;
	}

	public void setWinDelta(int winDelta) {
		this.winDelta = winDelta;
	}
}
