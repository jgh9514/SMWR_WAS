package com.admin.code.rest;

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

import com.admin.code.service.CdRelService;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Code Relation", description = "코드 관계 관리 API")
@RestController
@RequestMapping("/api/v1/common/sm")
public class CdRelController {

	@Autowired
	private CdRelService  service;
	
	@Operation(summary = "상위 코드 목록 조회", description = "상위 코드 목록을 조회합니다.")
	@PostMapping("/up-cd-list")
	public ResponseEntity<?> selectCdList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectCdList(param);

		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "팝업 코드 목록 조회", description = "팝업용 코드 목록을 조회합니다.")
	@PostMapping("/pop-cd-list")
	public ResponseEntity<?> selectPopCdList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectPopCdList(param);

		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "코드 관계 목록 조회", description = "코드 간의 관계 목록을 조회합니다.")
	@PostMapping("/cd-rel-list")
	public ResponseEntity<?> selectCdRelList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectCdRelList(param);

		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "코드 관계 저장", description = "코드 관계를 생성, 수정, 삭제합니다.")
	@SuppressWarnings("unchecked")
	@PostMapping("/cd-rel-save")
	@Transactional
	public ResponseEntity<?> insertCdRelList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, String>> insertList = (List<Map<String, String>>)param.get("insertRow");
		List<Map<String, String>> updateList = (List<Map<String, String>>)param.get("updateRow");
		List<Map<String, String>> deleteList = (List<Map<String, String>>)param.get("deleteRow");

		int n = -1;
		
		for(int i = 0; i < deleteList.size(); i++) {
			Map<String, String> temp = deleteList.get(i);
			temp.put("up_cd_grp_no", param.get("up_cd_grp_no").toString());
			temp.put("up_cd", param.get("up_cd").toString());
			 n = service.deleteCdRel(temp);
		}	
		
		for(int i = 0; i < updateList.size(); i++) {
			Map<String, String> temp = updateList.get(i);
			temp.put("up_cd_grp_no", param.get("up_cd_grp_no").toString());
			temp.put("up_cd", param.get("up_cd").toString());
			n = service.updateCdRel(temp);
		}
		
		for(int i = 0; i < insertList.size(); i++) {
			Map<String, String> temp = insertList.get(i);
			temp.put("up_cd_grp_no", param.get("up_cd_grp_no").toString());
			temp.put("up_cd", param.get("up_cd").toString());
			n = service.insertCdRel(temp);
		}
		
		Map<String, String> resultMap = new HashMap<>();
		String result = n > -1?  Constant.SUCCESS : Constant.FAIL;
		resultMap.put("result", result);

		return new ResponseEntity<>(resultMap, HttpStatus.OK);
	}
}
