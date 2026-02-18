package com.smw.guild.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuildJoinApplicationMapper {
	List<Map<String, ?>> selectJoinApplicationList(Map<String, Object> param);
	Map<String, ?> selectMyPendingJoinApplication(Map<String, Object> param);
	int insertJoinApplication(Map<String, Object> param);
	int updateJoinApplicationStatus(Map<String, Object> param);
	Map<String, ?> selectJoinApplicationDetail(Map<String, Object> param);
	int cancelMyPendingJoinApplication(Map<String, Object> param);
}


