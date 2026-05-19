package com.smw.monster.service;

import java.util.Map;

import com.smw.monster.dto.response.SiegeApiArchiveResponse;
import com.smw.monster.dto.response.SiegeBattleLogListResponse;
import com.smw.monster.dto.response.SiegeBattleReplayResponse;

public interface SiegeCollectorService {

	SiegeBattleLogListResponse getMatchBattleLogs(Map<String, Object> param);

	SiegeBattleReplayResponse getBattleReplay(long rid, Map<String, Object> param);

	SiegeApiArchiveResponse getLatestApiArchive(Map<String, Object> param);
}
