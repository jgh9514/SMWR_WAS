package com.sysconf.exception;

/**
 * 점령전 전적 조회 — 세션 길드와 대상 wizard_id 불일치 등 접근 거부.
 */
public class GuildBattleRecordAccessException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public GuildBattleRecordAccessException(String message) {
		super(message);
	}
}
