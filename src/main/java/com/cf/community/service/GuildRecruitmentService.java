package com.cf.community.service;

import java.util.Map;

public interface GuildRecruitmentService {

	Map<String, Object> getList(Map<String, Object> param);

	Map<String, ?> getDetail(Map<String, Object> param);

	Map<String, Object> save(Map<String, Object> param);

	Map<String, Object> delete(Map<String, Object> param);
}
