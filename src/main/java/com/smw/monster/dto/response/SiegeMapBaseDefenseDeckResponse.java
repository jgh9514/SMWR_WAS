package com.smw.monster.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeMapBaseDefenseDeckResponse {
	private final long deckId;
	private final String wizardId;
	private final String wizardName;
	private final Integer wizardLevel;
	private final String guildId;
	private final int deckStatus;
	private final Integer winCount;
	private final Integer loseCount;
	private final Integer drawCount;
	private final Integer totalCount;
	private final BigDecimal winningRate;
	private final String attackWizardId;
	private final Long battleStartTime;
	private final List<SiegeMapBaseDefenseUnitResponse> units;
}
