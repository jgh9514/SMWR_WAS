package com.smw.monster.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiegeMapLayoutMasterResponse {
	private final List<SiegeMapBaseLayoutItemResponse> layouts;
	private final List<SiegeMapBaseImageItemResponse> images;
}
