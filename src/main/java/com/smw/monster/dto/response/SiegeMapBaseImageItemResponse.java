package com.smw.monster.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeMapBaseImageItemResponse {
	private final String castleZone;
	private final String ringKind;
	private final Integer baseStatus;
	private final String imagePath;
}
