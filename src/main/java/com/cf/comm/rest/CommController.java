package com.cf.comm.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.user.service.UserService;
import com.cf.comm.service.CommService;
import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Common", description = "공통 API")
@RestController
@RequestMapping("/api/v1/comm")
public class CommController {
	
	@Autowired
	CommService service;

	@Autowired
	UserService userService;

	@Autowired
	private CookieUtil cookieUtil;

	@Operation(summary = "메뉴 목록 조회", description = "전체 메뉴 목록을 조회합니다.")
	@PostMapping("/menus")
	public ResponseEntity<?> selectMenuList(HttpSession session, HttpServletRequest request) {
		Map<String, Object> param = new HashMap<>();

		List<Map<String, ?>> list = service.selectMenuList(param);

		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "로그인 접근 페이지 목록 조회", description = "로그인 후 접근 가능한 페이지 목록을 조회합니다.")
	@PostMapping("/login-access-page-list")
	public ResponseEntity<?> selectLoginAccessPageList(HttpSession session, HttpServletRequest request) {
		Map<String, Object> param = new HashMap<>();

		List<Map<String, ?>> list = service.selectLoginAccessPageList(param);

		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "공통 코드 목록 조회", description = "공통 코드 목록을 그룹별로 조회합니다.")
	@PostMapping("/comm-cd")
	public ResponseEntity<?> selectCdList(@RequestBody Map<String, Object> param, HttpServletRequest request, HttpSession session) {
		List<Map<String, ?>> res = service.selectCdList(param);
		Map<String, List<Map<String, ?>>> grouped = new HashMap<>();
		for (Map<String, ?> item : res) {
		    String grpNo = (String) item.get("cd_grp_no");
		    grouped.computeIfAbsent(grpNo, k -> new ArrayList<>()).add(item);
		}
		return new ResponseEntity<>(grouped, HttpStatus.OK);
	}

	@Operation(summary = "그룹별 코드 조회", description = "특정 그룹의 코드 목록을 조회합니다.")
	@PostMapping("/comm-cd/{id}")
	public ResponseEntity<?> selectCdListByCdGrpNo(@PathVariable String id, HttpServletRequest request, HttpSession session) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("cd_grp_no", id);
		List<Map<String, ?>> searchList = service.selectCdList(param);

		return new ResponseEntity<>(searchList, HttpStatus.OK);
	}
	
	@Operation(summary = "설정 업데이트", description = "사용자 설정을 업데이트합니다.")
	@PostMapping("/config")
	public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request, HttpServletResponse response) throws Exception {
		service.updateConfig(param);

    	Map<String, Object> userInfo = userService.selectUserInfo(param);

		cookieUtil.refreshtoken(request, response, userInfo, Constant.LOGIN_TOKEN_NAME);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@Operation(summary = "다국어 i18n 조회", description = "다국어 i18n 데이터를 조회합니다.")
	@PostMapping("/mlang/i18n")
	public ResponseEntity<?> selectMultiLanguageI18n(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, String> multiLang = service.selectMultiLanguageI18n(param);
		return new ResponseEntity<>(multiLang, HttpStatus.OK);
	}

	@Operation(summary = "버전 체크", description = "애플리케이션 버전을 확인합니다.")
	@PostMapping("/version-check")
	public ResponseEntity<?> selectVersionCheck(HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> version = service.selectVersionCheck();

		return new ResponseEntity<>(version, HttpStatus.OK);
	}
	
	@Operation(summary = "에러 로그 저장", description = "프론트엔드 에러 로그를 저장합니다.")
	@PostMapping("/error-logs-save")
	public ResponseEntity<?> insertErrorLogs(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request, HttpServletResponse response) throws Exception {
		service.insertErrorLogs(param);
		return new ResponseEntity<>(HttpStatus.OK);
	}

}
