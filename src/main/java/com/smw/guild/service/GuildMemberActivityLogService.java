package com.smw.guild.service;

import java.util.Map;

/**
 * 길드원 활동 이력 기록 (본 업무 트랜잭션과 분리된 REQUIRES_NEW INSERT).
 */
public interface GuildMemberActivityLogService {

	void tryLogDeckRegister(Map<String, Object> param, String deckId);

	void tryLogDeckUpdate(Map<String, Object> param, Map<String, ?> deck);

	void tryLogDeckDelete(Map<String, Object> param, Map<String, ?> deck);

	void tryLogDeckVote(Map<String, Object> param, String deckId, String vote);

	void tryLogDefenseDeckRegister(Map<String, Object> param);
}
