package com.admin.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.admin.user.mapper.UserMapper;
import com.sysconf.security.AdminPrivilegeResolver;
import com.sysconf.util.DateUtil;

@Service
@Primary
public class UserServiceImpl implements UserService {

	@Autowired 
	DateUtil dateUtil;
	
	@Autowired 
	UserMapper mapper;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;
	
    @Override
    public Map<String, Object> selectUserInfo(Map<String, Object> param) {
    	Map<String, Object> userInfo = mapper.selectUserInfo(param);
    	
    	if (userInfo == null) {
    		return null;
    	}
    	enrichUserRolesAndAdminFlag(userInfo);
        return userInfo;
    }

	@Override
	public void enrichUserRolesAndAdminFlag(Map<String, Object> userInfo) {
		if (userInfo == null) {
			return;
		}
		Object uidObj = userInfo.get("user_id");
		if (uidObj == null) {
			uidObj = userInfo.get("sess_user_id");
		}
		if (uidObj == null) {
			userInfo.put("roles", Collections.emptyList());
			userInfo.put("is_admin", Boolean.FALSE);
			return;
		}
		Map<String, Object> param = new HashMap<>();
		param.put("user_id", uidObj.toString());
		List<Map<String, Object>> rows = mapper.selectUserRoles(param);
		List<Map<String, Object>> roles = new ArrayList<>();
		if (rows != null) {
			for (Map<String, Object> row : rows) {
				Map<String, Object> one = new HashMap<>();
				one.put("role_id", row.get("role_id"));
				one.put("role_nm", row.get("role_nm"));
				one.put("usg_yn", row.get("usg_yn"));
				roles.add(one);
			}
		}
		userInfo.put("roles", roles);
		userInfo.put("is_admin", Boolean.valueOf(adminPrivilegeResolver.isAdminUser(userInfo)));
	}

	@Override
	public int countUserByEmail(Map<String, Object> param) {
		return mapper.countUserByEmail(param);
	}
	
	@Override
	public List<Map<String, ?>> selectUserPopList(Map<String, Object> param) {
		return mapper.selectUserPopList(param);
	}

	@Override
	public List<Map<String, ?>> selectUserList(Map<String, Object> param) {
		return mapper.selectUserList(param);
	}

	@Override
	public List<Map<String, ?>> selectMytask(Map<String, Object> param) {
		return mapper.selectMytask(param);
	}

	@Override
	public int updateUserList(Map<String, Object> param) {
		return mapper.updateUserList(param);
	}

	@Override
	public Map<String, ?> selectUserDtl(Map<String, Object> param) {
		Map<String, ?> returnMap = mapper.selectUserDtl(param);
		if (returnMap != null) {
			((Map<String, Object>) returnMap).remove("user_pw");
		}
		return returnMap;
	}

	@Override
	public int updateUserDtl(Map<String, Object> param) {
		return mapper.updateUserDtl(param);
	}

	@Override
	public Map<String, ?> selectUserId() {
		return mapper.selectUserId();
	}

	@Override
	public int insertUserDtl(Map<String, Object> param) {
		return mapper.insertUserDtl(param);
	}

	@Override
	public int saveResetPassword(Map<String, Object> param) {
		return mapper.saveResetPassword(param);
	}

	@Override
	public List<Map<String, Object>> selectuserAthtInfo(Map<String, Object> map) {
		List<Map<String, ?>> result = mapper.selectuserAthtInfo(map);
		List<Map<String, Object>> convertedResult = new ArrayList<>();
		for (Map<String, ?> item : result) {
			Map<String, Object> convertedItem = new HashMap<>();
			for (Map.Entry<String, ?> entry : item.entrySet()) {
				convertedItem.put(entry.getKey(), entry.getValue());
			}
			convertedResult.add(convertedItem);
		}
		return convertedResult;
	}

	@Override
	public void updateAthtSttCd(Map<String, Object> athtMap) {
		Map<String, String> stringMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : athtMap.entrySet()) {
			stringMap.put(entry.getKey(), entry.getValue().toString());
		}
		mapper.updateAthtSttCd(stringMap);
	}

	@Override
	public List<Map<String, String>> selectAthtInfo(Map<String, Object> param) {
		return mapper.selectAthtInfo(param);
	}

	@Override
	public void insertuserAthtInfo(Map<String, Object> paramMap) {
		mapper.insertuserAthtInfo(paramMap);
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> buildTree(List<Map<String, Object>> data) {
		Map<String, Map<String, Object>> nodeMap = new HashMap<>();
		List<Map<String, Object>> rootNodes = new ArrayList<>();

		for (Map<String, Object> row : data) {
			Map<String, Object> node = new HashMap<>(row);
			node.put("children", new ArrayList<Map<String, Object>>());
			nodeMap.put((String) row.get("id"), node);
		}

		for (Map<String, Object> node : nodeMap.values()) {
			String upDeptCd = (String) node.get("up_dept_cd");
			if ("$0".equals(upDeptCd)) {
				rootNodes.add(node);
			} else {
				Map<String, Object> parentNode = nodeMap.get(upDeptCd);
				if (parentNode != null) {
					List<Map<String, Object>> children = (List<Map<String, Object>>) parentNode.get("children");
					children.add(node);
				}
			}
		}

		return rootNodes;
	}
	
	@Override
	public List<Map<String, ?>> selectuserName(Map<String, Object> param) {
		return mapper.selectuserName(param);
	}

	@Override
	public void updateDvcId(Map<String, Object> param) {
		mapper.updateDvcId(param);
	}

	@Override
	public void updateSiegeViewScope(Map<String, Object> param) {
		mapper.updateSiegeViewScope(param);
	}
}
