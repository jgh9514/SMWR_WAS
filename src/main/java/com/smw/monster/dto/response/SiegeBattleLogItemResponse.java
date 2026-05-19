package com.smw.monster.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeBattleLogItemResponse {
	private final String logId;
	private final String logTimestamp;
	private final String matchId;
	private final Integer baseNumber;
	private final String guildId;
	private final String wizardId;
	private final String wizardName;
	private final String oppGuildId;
	private final String oppWizardId;
	private final String oppWizardName;
	private final String winLose;
	private final Long replayRidRef;
	private final String battleDesc;
	private final Integer matchScoreVar;
	private final Integer wizardLevel;
	private final Integer oppWizardLevel;
	private final Short logTypeApi;
	private final String guildName;
	private final String oppGuildName;
	private final boolean fromCollector;
}
