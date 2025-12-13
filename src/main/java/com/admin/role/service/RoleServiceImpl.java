package com.admin.role.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.multilang.service.MultiLangService;
import com.admin.role.mapper.RoleMapper;
import com.sysconf.constants.Constant;
import com.sysconf.util.DateUtil;

@Service
public class RoleServiceImpl implements RoleService {

	@Autowired
	DateUtil dateUtil;

	@Autowired
	RoleMapper mapper;

	@Autowired
	private MultiLangService mlangService;

	@Override
	public List<Map<String, ?>> selectRoleList(Map<String, Object> param) {
		return mapper.selectRoleList(param);
	}

	@Override
	public String selectRolekey() {
		return mapper.selectRolekey();
	}

	@Override
	@Transactional
	public String insertRole(Map<String, Object> param) {
		String roleId = selectRolekey();
		param.put("role_id", roleId);
		param.put("sort_sn", param.get("srt_sn"));
		param.put("role_nm", param.get("role_nm"));
		mapper.insertRole(param);

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_ROLE);
		mlangParam.put("bsns_cd", Constant.BSNS_COMMON);
		mlangParam.put("mlang_id", param.get("role_id").toString());
		mlangParam.put("mlang_txt", param.get("role_nm").toString());
		mlangParam.put("lang_cd", param.get("lang_cd").toString());
		mlangParam.put("user_id", param.get("user_id").toString());
		mlangService.insertMlang(mlangParam);

		return "";
	}

	@Override
	@Transactional
	public int updateRole(Map<String, Object> param) {
		param.put("sort_sn", param.get("srt_sn"));
		mapper.updateRole(param);

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_ROLE);
		mlangParam.put("mlang_id", param.get("role_id").toString());
		mlangParam.put("mlang_txt", param.get("role_nm").toString());
		mlangParam.put("lang_cd", param.get("lang_cd").toString());
		mlangParam.put("user_id", param.get("user_id").toString());
		mlangService.updateMlang(mlangParam);

		return 1;
	}

	@Override
	@Transactional
	public int deleteRole(Map<String, Object> param) {
		mapper.deleteRole(param);

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_ROLE);
		mlangParam.put("mlang_id", param.get("role_id").toString());
		mlangService.deleteMlang(mlangParam);

		return 1;
	}

	@Override
	public List<Map<String, ?>> selectUserRoleList(Map<String, Object> param) {
		return mapper.selectUserRoleList(param);
	}

	@Override
	public List<Map<String, ?>> selectRoleListMeunRole(Map<String, Object> param) {
		return mapper.selectRoleListMeunRole(param);
	}

	@Override
	public List<Map<String, ?>> selectMenuListMeunRole(Map<String, Object> param) {
		return mapper.selectMenuListMeunRole(param);
	}

	@Override
	public int updateMeunRoleList(Map<String, Object> param) {
		return mapper.updateMeunRoleList(param);
	}

	@Override
	public int deleteMeunRoleList(Map<String, Object> param) {
		return mapper.deleteMeunRoleList(param);
	}

	@Override
	public List<Map<String, ?>> selectApiListApiRole(Map<String, Object> param) {
		return mapper.selectApiListApiRole(param);
	}

	@Transactional
	@Override
	public int insertApiRoleList(Map<String, Object> param) {
		return mapper.insertApiRoleList(param);
	}

	@Override
	public int insertUserRole(Map<String, Object> param) {
		mapper.insertUserRole(param);
		return 1;
	}

	@Override
	public void updateUserRoleList(Map<String, Object> saveMap) {
		Map<String, String> stringMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : saveMap.entrySet()) {
			stringMap.put(entry.getKey(), entry.getValue().toString());
		}
		mapper.updateUserRoleList(stringMap);
	}

	// 메뉴 권한

	@Override
	public int upsertMenuRole(Map<String, Object> param) {
		return mapper.upsertMenuRole(param);
	}

}
