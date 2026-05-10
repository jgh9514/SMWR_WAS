package com.smw.rta.model;

public class RtaRankCutSnapRow {

	private String gradeSlot;
	private short sortOrder;
	private long cutoffScore;

	public RtaRankCutSnapRow() {
	}

	public RtaRankCutSnapRow(String gradeSlot, short sortOrder, long cutoffScore) {
		this.gradeSlot = gradeSlot;
		this.sortOrder = sortOrder;
		this.cutoffScore = cutoffScore;
	}

	public String getGradeSlot() { return gradeSlot; }
	public void setGradeSlot(String gradeSlot) { this.gradeSlot = gradeSlot; }

	public short getSortOrder() { return sortOrder; }
	public void setSortOrder(short sortOrder) { this.sortOrder = sortOrder; }

	public long getCutoffScore() { return cutoffScore; }
	public void setCutoffScore(long cutoffScore) { this.cutoffScore = cutoffScore; }
}
