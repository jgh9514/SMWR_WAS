package com.smw.monster.dto.response;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeApiArchiveResponse {
	private final long id;
	private final String command;
	private final Long siegeId;
	private final String matchId;
	private final long capturedAt;
	private final Short logType;
	private final Short baseNumber;
	private final Long replayRid;
	private final String source;
	private final Map<String, Object> payload;
}
