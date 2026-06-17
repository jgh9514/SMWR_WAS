package com.smw.guild.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuildMemberActivityLogMapper {

	int insertGuildMemberActivityLog(Map<String, Object> param);

	int countGuildMemberActivityLog(Map<String, Object> param);

	List<Map<String, ?>> selectGuildMemberActivityLogList(Map<String, Object> param);

	List<Map<String, ?>> selectMonsterKrNamesByIds(Map<String, Object> param);
}
