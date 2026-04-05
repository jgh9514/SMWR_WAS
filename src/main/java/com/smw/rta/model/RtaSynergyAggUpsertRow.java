package com.smw.rta.model;

import java.sql.Timestamp;

/**
 * rta_synergy_agg 증분 UPSERT 용.
 */
public class RtaSynergyAggUpsertRow {

	private int arity;
	private long m1;
	private long m2;
	private long m3;
	private int winDelta;
	private Timestamp lastMatchAt;

	public RtaSynergyAggUpsertRow() {
	}

	public RtaSynergyAggUpsertRow(int arity, long m1, long m2, long m3, int winDelta, Timestamp lastMatchAt) {
		this.arity = arity;
		this.m1 = m1;
		this.m2 = m2;
		this.m3 = m3;
		this.winDelta = winDelta;
		this.lastMatchAt = lastMatchAt;
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

	public int getWinDelta() {
		return winDelta;
	}

	public void setWinDelta(int winDelta) {
		this.winDelta = winDelta;
	}

	public Timestamp getLastMatchAt() {
		return lastMatchAt;
	}

	public void setLastMatchAt(Timestamp lastMatchAt) {
		this.lastMatchAt = lastMatchAt;
	}
}
