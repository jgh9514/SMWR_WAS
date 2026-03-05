package com.admin.page.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.page.service.PageService;
import com.sysconf.interceptor.PageSearchResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Page Management", description = "페이지 관리 API")
@RestController
@RequestMapping("/api/v1/sm")
public class PageController {

    @Autowired
    private PageService service;
    
	@Autowired
	private PageSearchResult pageSearchResult;

    @Operation(summary = "페이지 검색 항목 조회", description = "페이지의 검색 조건 항목을 조회합니다.")
    @PostMapping("/page-search-list")
    public ResponseEntity<?> selectPageConditionItems(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
        List<Map<String, Object>> list = service.selectPageConditionItems(param);

		return ResponseEntity.ok(list);
    }

	@Operation(summary = "페이지 목록 조회", description = "등록된 페이지 목록을 조회합니다.")
	@PostMapping("/page-list")
	public ResponseEntity<?> selectPageList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectPageList(param);

		return ResponseEntity.ok(list);
	}

	@Operation(summary = "페이지 검색 조건 조회", description = "페이지의 검색 조건을 조회합니다.")
	@PostMapping("/page-search")
	public ResponseEntity<?> selectPageConditionList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectPageConditionList(param);

		return ResponseEntity.ok(list);
	}

	@Operation(summary = "페이지 저장", description = "페이지 정보와 검색 조건을 생성, 수정, 삭제합니다.")
	@SuppressWarnings("unchecked")
	@PostMapping("/page-save")
	@Transactional
	public ResponseEntity<?> insertPage(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {		
		List<Map<String, Object>> insertPageList = (List<Map<String, Object>>)param.get("insertPageList");
		List<Map<String, Object>> updatePageList = (List<Map<String, Object>>)param.get("updatePageList");
		List<Map<String, Object>> deletePageList = (List<Map<String, Object>>)param.get("deletePageList");

		List<Map<String, Object>> updateConditionList = (List<Map<String, Object>>)param.get("updateConditionList");
		List<Map<String, Object>> deleteConditionList = (List<Map<String, Object>>)param.get("deleteConditionList");

		for (Map<String, Object> item : deletePageList) {
		    service.deletePage(item);
		}

		for (Map<String, Object> item : insertPageList) {
			service.insertPage(item);
		}

		for (Map<String, Object> item : updatePageList) {
			service.updatePage(item);
		}
		
		for (Map<String, Object> item : deleteConditionList) {
			item.put("page_id", param.get("active_page_id").toString());
			service.deletePageCondition(item);
		}

		for (Map<String, Object> item : updateConditionList) {
			item.put("page_id", param.get("active_page_id").toString());
			service.updatePageCondition(item);
		}

		pageSearchResult.setPageConditionList();

		return ResponseEntity.ok(true);
	}

	@Operation(summary = "페이지 조건 정보 조회", description = "특정 페이지의 조건 정보를 조회합니다.")
	@PostMapping("/page-condition-list")
	public ResponseEntity<?> selectPageCondition(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, ?> info = service.selectPageConditionInfo(param);

		return ResponseEntity.ok(info);
	}

	@Operation(summary = "페이지 조건 저장", description = "페이지의 검색 조건을 저장합니다.")
	@PostMapping("/page-condition-save")
	public ResponseEntity<?> insertPageCondition(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		service.savePageCondition(param);

		return ResponseEntity.ok(true);
	}

	@Operation(summary = "페이지 조건 삭제", description = "페이지의 검색 조건을 삭제합니다.")
	@PostMapping("/page-condition/{id}")
	public ResponseEntity<?> deletePageCondition(@PathVariable String id, HttpSession session, HttpServletRequest request) {
		Map<String, Object> param = new HashMap<>();
		param.put("condition_id", id);
		
		service.deletePageCondition(param);
		
		return ResponseEntity.ok(true);
	}

	@Operation(summary = "페이지 권한 조회", description = "페이지의 접근 권한을 조회합니다.")
	@PostMapping("/page-auth")
	public ResponseEntity<?> selectPageAuth(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = service.selectPageAuth(param);

		return ResponseEntity.ok(result);
	}
}
