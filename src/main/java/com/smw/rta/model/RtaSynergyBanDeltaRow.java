package com.smw.rta.model;

/**
 * {@code rta_agg_synergy_combo.ban_cnt} 증분 (시즌×콤보 단위, combo_size=1 행 전부 동일 증가).
 */
public class RtaSynergyBanDeltaRow {

	private long seasonId;
	private String comboUnitKey;
	private long delta;

	public RtaSynergyBanDeltaRow() {
	}

	public RtaSynergyBanDeltaRow(long seasonId, String comboUnitKey, long delta) {
		this.seasonId = seasonId;
		this.comboUnitKey = comboUnitKey;
		this.delta = delta;
	}

	public long getSeasonId() {
		return seasonId;
	}

	public void setSeasonId(long seasonId) {
		this.seasonId = seasonId;
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
