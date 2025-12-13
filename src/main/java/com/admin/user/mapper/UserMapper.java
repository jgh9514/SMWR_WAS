package com.admin.user.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
	
	public Map<String, Object> selectUserInfo(Map<String, Object> param);

	public List<Map<String, Object>> selectDeptTreeList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectDeptSearchList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectUserPopList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectUserList(Map<String, Object> param);
	public List<Map<String, ?>> selectMytask(Map<String, Object> param);

	public int updateUserList(Map<String, Object> param);
	
	public Map<String, ?> selectUserDtl(Map<String, Object> param);

	public int updateUserDtl(Map<String, Object> param);

	public Map<String, ?> selectUserId();

	public int insertUserDtl(Map<String, Object> param);
	
	public int saveResetPassword(Map<String, Object> param);

	public List<Map<String, ?>> selectuserAthtInfo(Map<String, Object> param);

	public void updateAthtSttCd(Map<String, String> athtMap);

	public List<Map<String, String>> selectAthtInfo(Map<String, Object> param);

	public void insertuserAthtInfo(Map<String, Object> paramMap);

	public List<Map<String, ?>> selectuserName(Map<String, Object> param);
	
	public Map<String, Object> selectDvcId(Map<String, Object> param);
	public List<String> selectUserWkplRoles(Map<String, Object> param);
	public Map<String, String> selectDeptChfuserInfo(Map<String, Object> param);

	public void insertMsg(Map<String, Object> param);
	public void insertTwoFactorAuthCode(Map<String, Object> param);
	public Map<String, Object> selectTwoFactorAuthCode(Map<String, Object> param);
	public void callProcMsg();
	
	public void updateSiegeViewScope(Map<String, Object> param);
	
	public int updateDvcId(Map<String, Object> param);
	
	public void insertUserLoginLog(Map<String, Object> param);
	
	public List<Map<String, ?>> selectLastLoginHst(Map<String, Object> param);
	
	public int updateLangCd(Map<String, Object> param);
}
