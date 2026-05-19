package com.smw.monster.dto.request;

import java.util.Map;

import lombok.Data;

@Data
public class SiegeMapSnapshotUploadRequest {
	/** GetGuildSiegeMatchupInfo ret_code=0 응답 본문 */
	private Map<String, Object> matchup;
	private String source;
}
