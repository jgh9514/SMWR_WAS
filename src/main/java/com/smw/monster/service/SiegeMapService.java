package com.smw.monster.service;

import java.util.List;
import java.util.Map;

import com.smw.monster.dto.request.SiegeMapSnapshotUploadRequest;

public interface SiegeMapService {

	Map<String, Object> ingestMatchupSnapshot(SiegeMapSnapshotUploadRequest request);

	Map<String, Object> getMapView(String matchId, Long snapshotId, String myGuildId);

	List<Map<String, ?>> getMatchHistory(Map<String, Object> param);

	int getMatchHistoryCount(Map<String, Object> param);

	List<Map<String, ?>> getSnapshotTimeline(String matchId);

	com.smw.monster.dto.response.SiegeMapBaseDefenseResponse getBaseDefense(
			String matchId,
			int baseNumber,
			Long snapshotId);

	com.smw.monster.dto.response.SiegeMapLayoutMasterResponse getLayoutMaster();

}
