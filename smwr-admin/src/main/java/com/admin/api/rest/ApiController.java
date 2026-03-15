package com.admin.api.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.api.service.ApiService;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "API Management", description = "API 관리 API")
@RestController
@RequestMapping("/api/v1/common/sm")
public class ApiController {
	
	@Autowired
	private ApiService service;
	
	@Operation(summary = "API 목록 조회", description = "등록된 API 목록을 조회합니다.")
	@PostMapping("/api-list")
	public ResponseEntity<?> selectApiList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectApiList(param);

		return ResponseEntity.ok(list);
	}
	
	@Operation(summary = "API 역할 목록 조회", description = "API에 매핑된 역할 목록을 조회합니다.")
	@PostMapping("/api-role")
	public ResponseEntity<?> selectApiRoleList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectApiRoleList(param);
		return ResponseEntity.ok(list);
	}
	
	@Operation(summary = "API 저장", description = "API를 생성, 수정, 삭제하고 역할을 매핑합니다.")
	@PostMapping("/api-save")
	@SuppressWarnings("unchecked")
	@Transactional
	public ResponseEntity<?> insertApiList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {		
		List<Map<String, Object>> insertList = (List<Map<String, Object>>)param.get("insertRow");
		List<Map<String, Object>> updateList = (List<Map<String, Object>>)param.get("updateRow");
		List<Map<String, Object>> deleteList = (List<Map<String, Object>>)param.get("deleteRow");

		Map<String, Object> chkData = (HashMap<String, Object>)param.get("chkData");
		List<Map<String, Object>> updateValue = (List<Map<String, Object>>)chkData.get("updateValue");
		
		String key = "";
		for(int i=0; i<insertList.size(); i++) {
			Map<String, Object> temp = insertList.get(i);
			key = service.selectApiKey(temp);
			temp.put("api_id", key);

			service.insertApiList(temp);
		}
		for(int i=0; i<updateList.size(); i++) {
			Map<String, Object> temp = updateList.get(i);

			service.updateApiList(temp);
		}
		for(int i=0; i<deleteList.size(); i++) {
			Map<String, Object> temp = deleteList.get(i);

			service.deleteApiList(temp);
			service.deleteApiRole(temp);
		}
		
		Map<String, Object> tempMap;
		
		int n = 0;

		for(int i = 0; i < updateValue.size(); i++) {
			tempMap = updateValue.get(i);
			if(chkData.get("api_id") == null || "".equalsIgnoreCase(chkData.get("api_id").toString())){
				tempMap.put("api_id", key);
			}else {
				tempMap.put("api_id", chkData.get("api_id").toString());
			}
			n = service.insertApiRole(tempMap); 
		}

		Map<String, String> resultMap = new HashMap<>();
		String result = n > -1? Constant.SUCCESS : Constant.FAIL;
		resultMap.put("result", result);

		return new ResponseEntity<>(resultMap, HttpStatus.OK);
	}
}
