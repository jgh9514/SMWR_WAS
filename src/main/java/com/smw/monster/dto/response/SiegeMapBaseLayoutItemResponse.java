package com.smw.monster.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeMapBaseLayoutItemResponse {
	private final int gameBaseNumber;
	private final String castleZone;
	private final int slotNo;
	private final BigDecimal posXPct;
	private final BigDecimal posYPct;
	private final String ringKind;
	private final int displayWidthPx;
	private final int displayHeightPx;
}
