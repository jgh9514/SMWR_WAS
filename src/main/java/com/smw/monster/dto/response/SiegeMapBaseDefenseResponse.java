package com.smw.monster.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeMapBaseDefenseResponse {
	private final String matchId;
	private final int baseNumber;
	private final Long captureId;
	private final Long capturedAt;
	private final Integer baseStatus;
	private final String guildId;
	private final Integer remainSec;
	private final List<SiegeMapBaseDefenseDeckResponse> decks;
}
