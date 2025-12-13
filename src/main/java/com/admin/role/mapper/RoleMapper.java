package com.admin.role.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper {
	// 메뉴 권한
	public int upsertMenuRole(Map<String, Object> param);

	public List<Map<String, ?>> selectRoleList(Map<String, Object> param);
	
	public String selectRolekey();
	
	public String insertRole(Map<String, Object> param);
	
	public int updateRole(Map<String, Object> param);
	
	public int deleteRole(Map<String, Object> param);
	
	public List<Map<String, ?>> selectUserRoleList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectRoleListMeunRole(Map<String, Object> param);
	
	public List<Map<String, ?>> selectMenuListMeunRole(Map<String, Object> param);
	
	public int updateMeunRoleList(Map<String, Object> param);
	
	public int deleteMeunRoleList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectApiListApiRole(Map<String, Object> param);
	
	public int insertApiRoleList(Map<String, Object> param);

	public int insertUserRole(Map<String, Object> param);

	public void updateUserRoleList(Map<String, String> saveMap);
	
	public List<Map<String, ?>> selectApiRoleList(Map<String, Object> param);
}
