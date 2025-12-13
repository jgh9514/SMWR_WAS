package com.admin.user.service;

import java.util.List;
import java.util.Map;

public interface UserService {
	
	public Map<String, Object> selectUserInfo(Map<String, Object> param);
		
	public List<Map<String, ?>> selectUserPopList(Map<String, Object> param);

	public List<Map<String, ?>> selectUserList(Map<String, Object> param);
	public List<Map<String, ?>> selectMytask(Map<String, Object> param);

	public int updateUserList(Map<String, Object> param);
	
	public Map<String, ?> selectUserDtl(Map<String, Object> param);
	
	public int insertUserDtl(Map<String, Object> param); 
	
	public int updateUserDtl(Map<String, Object> param); 
	
	public Map<String, ?> selectUserId();
	
	public int saveResetPassword(Map<String, Object> param);

	public List<Map<String, Object>> selectuserAthtInfo(Map<String, Object> map);

	public void updateAthtSttCd(Map<String, Object> athtMap);

	public List<Map<String, String>> selectAthtInfo(Map<String, Object> param);

	public void insertuserAthtInfo(Map<String, Object> paramMap);

	public List<Map<String, ?>> selectuserName(Map<String, Object> param);

	public void updateDvcId(Map<String, Object> param);
	
	public void updateSiegeViewScope(Map<String, Object> param);
}
