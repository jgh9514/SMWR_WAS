package com.smw.monster.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeBattleLogListResponse {
	private final String matchId;
	private final List<SiegeBattleLogItemResponse> list;
	private final int totalCount;
	private final int totalPage;
}
