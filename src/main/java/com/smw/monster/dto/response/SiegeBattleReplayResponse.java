package com.smw.monster.dto.response;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeBattleReplayResponse {
	private final long rid;
	private final String matchId;
	private final String battleDesc;
	private final String source;
	private final Map<String, Object> payload;
}
