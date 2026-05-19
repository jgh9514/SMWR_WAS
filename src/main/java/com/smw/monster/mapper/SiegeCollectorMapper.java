package com.smw.monster.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

public interface SiegeCollectorMapper {

	List<Map<String, ?>> selectMatchBattleLogList(Map<String, Object> param);

	int selectMatchBattleLogCount(Map<String, Object> param);

	Map<String, ?> selectBattleReplayRaw(@Param("rid") long rid);

	Map<String, ?> selectBattleReplayPayload(@Param("rid") long rid);

	int countGuildAccessToMatch(@Param("match_id") String matchId, @Param("guild_id") String guildId);

	Map<String, ?> selectLatestApiArchive(Map<String, Object> param);
}
