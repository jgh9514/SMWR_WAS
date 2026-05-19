package com.smw.monster.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeMapBaseDefenseUnitResponse {
	private final int posId;
	private final int unitMasterId;
	private final int unitLevel;
	private final String krName;
	private final String imageUrl;
}
