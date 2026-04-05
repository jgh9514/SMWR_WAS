package com.smw.rta.model;

/**
 * rta_synergy_match_fact 적재용 (솔로·듀오·트리오 공통, arity 로 구분).
 */
public class RtaSynergyComboRow {

	private long rid;
	private String wizardId;
	private int arity;
	private long m1;
	private long m2;
	private long m3;
	private boolean win;

	public RtaSynergyComboRow() {
	}

	public RtaSynergyComboRow(long rid, String wizardId, int arity, long m1, long m2, long m3, boolean win) {
		this.rid = rid;
		this.wizardId = wizardId;
		this.arity = arity;
		this.m1 = m1;
		this.m2 = m2;
		this.m3 = m3;
		this.win = win;
	}

	public long getRid() {
		return rid;
	}

	public void setRid(long rid) {
		this.rid = rid;
	}

	public String getWizardId() {
		return wizardId;
	}

	public void setWizardId(String wizardId) {
		this.wizardId = wizardId;
	}

	public int getArity() {
		return arity;
	}

	public void setArity(int arity) {
		this.arity = arity;
	}

	public long getM1() {
		return m1;
	}

	public void setM1(long m1) {
		this.m1 = m1;
	}

	public long getM2() {
		return m2;
	}

	public void setM2(long m2) {
		this.m2 = m2;
	}

	public long getM3() {
		return m3;
	}

	public void setM3(long m3) {
		this.m3 = m3;
	}

	public boolean isWin() {
		return win;
	}

	public void setWin(boolean win) {
		this.win = win;
	}
}
