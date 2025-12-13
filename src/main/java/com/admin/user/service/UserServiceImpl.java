package com.admin.user.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.admin.role.mapper.RoleMapper;
import com.admin.user.mapper.UserMapper;
import com.sysconf.util.DateUtil;

@Service
@Primary
public class UserServiceImpl implements UserService {

	@Autowired 
	DateUtil dateUtil;
	
	@Autowired 
	UserMapper mapper;
	
	@Autowired
	RoleMapper roleMapper;

    @Override
    public Map<String, Object> selectUserInfo(Map<String, Object> param) {
    	Map<String, Object> userInfo = mapper.selectUserInfo(param);
    	
    	if (userInfo == null) {
    		return null;
    	}
    	
		// 사용자 권한 정보 추가
		Map<String, Object> roleParam = new HashMap<>();
		roleParam.put("usr_id", userInfo.get("user_id"));
		List<Map<String, ?>> userRoles = roleMapper.selectUserRoleList(roleParam);
		userInfo.put("roles", userRoles);

        return userInfo;
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
