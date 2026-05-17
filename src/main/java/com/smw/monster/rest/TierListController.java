package com.smw.monster.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.monster.mapper.TierListMapper;
import com.sysconf.annotation.RequireLogin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Tier List", description = "사용자 티어리스트 저장/불러오기 API")
@RequireLogin
@RestController
@RequestMapping("/api/v1/summonerswar/tier-list")
public class TierListController {

    @Autowired
    private TierListMapper tierListMapper;

    @SuppressWarnings("unchecked")
    private String getSessUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("userInfo");
        if (attr instanceof Map) {
            Map<String, Object> userInfo = (Map<String, Object>) attr;
            Object v = userInfo.get("sess_user_id");
            return v != null ? v.toString() : null;
        }
        return null;
    }

    private ResponseEntity<?> unauthorized() {
        Map<String, Object> body = new HashMap<>();
        body.put("result", "FAIL");
        body.put("message", "로그인이 필요합니다.");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<?> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("result", "SUCCESS");
        body.put("data", data);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    private ResponseEntity<?> fail(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("result", "FAIL");
        body.put("message", message);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** 내 티어리스트 목록 조회 */
    @Operation(summary = "티어리스트 목록 조회", description = "로그인한 사용자의 저장된 티어리스트 목록을 반환합니다.")
    @PostMapping("/list")
    public ResponseEntity<?> list(HttpServletRequest request) {
        String userId = getSessUserId(request);
        if (userId == null) return unauthorized();

        Map<String, Object> param = new HashMap<>();
        param.put("user_id", userId);
        List<Map<String, Object>> result = tierListMapper.selectTierListByUser(param);
        return ok(result);
    }

    /** 티어리스트 저장 */
    @Operation(summary = "티어리스트 저장", description = "현재 티어리스트를 저장합니다. title, tier_data(JSON 문자열) 필수.")
    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        String userId = getSessUserId(request);
        if (userId == null) return unauthorized();

        String title = param.get("title") != null ? param.get("title").toString().trim() : "내 티어리스트";
        String tierData = param.get("tier_data") != null ? param.get("tier_data").toString() : null;
        if (tierData == null || tierData.isBlank()) return fail("tier_data가 비어있습니다.");
        if (title.length() > 200) title = title.substring(0, 200);

        Map<String, Object> insertParam = new HashMap<>();
        insertParam.put("user_id", userId);
        insertParam.put("title", title);
        insertParam.put("tier_data", tierData);

        int result = tierListMapper.insertTierList(insertParam);
        if (result < 1) return fail("저장에 실패했습니다.");

        return ok(insertParam.get("id"));
    }

    /** 티어리스트 수정 */
    @Operation(summary = "티어리스트 수정", description = "저장된 티어리스트를 덮어씁니다. id, title, tier_data 필수.")
    @PostMapping("/update")
    public ResponseEntity<?> update(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        String userId = getSessUserId(request);
        if (userId == null) return unauthorized();

        Object idObj = param.get("id");
        if (idObj == null) return fail("id가 필요합니다.");
        String tierData = param.get("tier_data") != null ? param.get("tier_data").toString() : null;
        if (tierData == null || tierData.isBlank()) return fail("tier_data가 비어있습니다.");

        String title = param.get("title") != null ? param.get("title").toString().trim() : "내 티어리스트";
        if (title.length() > 200) title = title.substring(0, 200);

        Map<String, Object> updateParam = new HashMap<>();
        updateParam.put("id", Long.parseLong(idObj.toString()));
        updateParam.put("user_id", userId);
        updateParam.put("title", title);
        updateParam.put("tier_data", tierData);

        int result = tierListMapper.updateTierList(updateParam);
        if (result < 1) return fail("수정 권한이 없거나 존재하지 않습니다.");

        return ok(null);
    }

    /** 티어리스트 삭제 */
    @Operation(summary = "티어리스트 삭제", description = "저장된 티어리스트를 삭제합니다. id 필수.")
    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        String userId = getSessUserId(request);
        if (userId == null) return unauthorized();

        Object idObj = param.get("id");
        if (idObj == null) return fail("id가 필요합니다.");

        Map<String, Object> deleteParam = new HashMap<>();
        deleteParam.put("id", Long.parseLong(idObj.toString()));
        deleteParam.put("user_id", userId);

        int result = tierListMapper.deleteTierList(deleteParam);
        if (result < 1) return fail("삭제 권한이 없거나 존재하지 않습니다.");

        return ok(null);
    }
}
