package com.cf.community.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuildRecruitmentMapper {

	List<Map<String, ?>> selectGuildRecruitmentList(Map<String, Object> param);

	int selectGuildRecruitmentCount(Map<String, Object> param);

	Map<String, ?> selectGuildRecruitmentDtl(Map<String, Object> param);

	int insertGuildRecruitment(Map<String, Object> param);

	int updateGuildRecruitment(Map<String, Object> param);

	int deleteGuildRecruitment(Map<String, Object> param);
}
