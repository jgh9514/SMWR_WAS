package com.admin.role.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.role.service.RoleService;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Role Management", description = "역할 관리 API")
@RestController
@RequestMapping("/api/v1/common/sm")
public class RoleController {

	@Autowired
	private RoleService service;

	@Operation(summary = "역할 목록 조회", description = "등록된 역할 목록을 조회합니다.")
	@PostMapping("/role-list")
	public ResponseEntity<?> selectRoleList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectRoleList(param);

		return ResponseEntity.ok(list);
	}

	@Operation(summary = "역할 저장", description = "역할을 생성, 수정, 삭제합니다.")
	@SuppressWarnings("unchecked")
	@PostMapping("/role-save")
	public ResponseEntity<?> saveRoleList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, Object>> insertList = (List<Map<String, Object>>) param.get("insertRow");
		List<Map<String, Object>> updateList = (List<Map<String, Object>>) param.get("updateRow");
		List<Map<String, Object>> deleteList = (List<Map<String, Object>>) param.get("deleteRow");

		for (Map<String, Object> item : deleteList) {
			service.deleteRole(item);
		}

		for (Map<String, Object> item : insertList) {
			service.insertRole(item);
		}

		for (Map<String, Object> item : updateList) {
			service.updateRole(item);
		}

		return ResponseEntity.ok(true);
	}

	@Operation(summary = "사용자 역할 목록 조회", description = "사용자에게 부여된 역할 목록을 조회합니다.")
	@PostMapping("/user-role-list")
	public ResponseEntity<?> selectUserRoleList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectUserRoleList(param);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "사용자 역할 저장", description = "사용자의 역할을 부여, 수정, 제거합니다.")
	@PostMapping("/user-role-save")
	@SuppressWarnings("unchecked")
	public ResponseEntity<?> updateUserRoleList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, Object>> insertList = (List<Map<String, Object>>) param.get("insertRow");
		List<Map<String, Object>> updateList = (List<Map<String, Object>>) param.get("updateRow");
		List<Map<String, Object>> deleteList = (List<Map<String, Object>>) param.get("deleteRow");

		int n = 0;

		for (int i = 0; i < deleteList.size(); i++) {
			Map<String, Object> saveMap = deleteList.get(i);
			saveMap.put("del_yn", "Y");
			service.updateUserRoleList(saveMap);
		}
		for (int i = 0; i < insertList.size(); i++) {
			Map<String, Object> saveMap = insertList.get(i);
			saveMap.put("del_yn", "N");
			service.updateUserRoleList(saveMap);
		}
		for (int i = 0; i < updateList.size(); i++) {
			Map<String, Object> saveMap = updateList.get(i);
			saveMap.put("del_yn", "N");
			service.updateUserRoleList(saveMap);
		}
		Map<String, Object> resultMap = new HashMap<>();
		String result;
		result = (n > -1 ? Constant.SUCCESS : Constant.FAIL);
		resultMap.put("result", result);

		return new ResponseEntity<>(resultMap, HttpStatus.OK);
	}
}
