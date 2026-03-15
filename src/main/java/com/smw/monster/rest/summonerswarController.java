package com.smw.monster.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.smw.monster.service.summonerswarService;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Summoners War", description = "Summoners War 관련 API")
@RestController
@RequestMapping("/api/v1/summonerswar")
public class summonerswarController {

	@Autowired
	summonerswarService swService;
	
	private Map<String, Object> getSessUserInfo(HttpServletRequest request) {
		Object attr = request != null ? request.getAttribute("userInfo") : null;
		if (attr instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) attr;
			return m;
		}
		return null;
	}

	private ResponseEntity<?> requireLoginAndGuild(HttpServletRequest request, Map<String, Object> param) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null || userInfo.get("sess_user_id") == null) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "로그인이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
		}
		if (userInfo.get("sess_guild_id") == null) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "길드 가입이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
		}
		// MyBatis 쿼리에서 사용할 수 있도록 명시적으로도 주입 (인터셉터가 주입하지만 방어적으로 추가)
		if (param != null) {
			param.put("sess_user_id", userInfo.get("sess_user_id"));
			param.put("sess_guild_id", userInfo.get("sess_guild_id"));
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private boolean isAdminUser(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null) return false;
		// SessionInterceptor/AuthInterceptor에서 roles는 sess_role로 주입됨 (하위호환으로 roles도 허용)
		Object rolesObj = userInfo.get("roles");
		if (rolesObj == null) {
			rolesObj = userInfo.get("sess_role");
		}
		if (!(rolesObj instanceof List)) return false;
		List<?> roles = (List<?>) rolesObj;
		for (Object r : roles) {
			if (!(r instanceof Map)) continue;
			Map<String, ?> role = (Map<String, ?>) r;
			Object roleId = role.get("role_id");
			Object usgYn = role.get("usg_yn");
			String roleIdStr = String.valueOf(roleId);
			// usg_yn이 없는 데이터도 있어 하위호환으로 허용(없으면 사용중으로 간주)
			boolean enabled = (usgYn == null) || "Y".equalsIgnoreCase(String.valueOf(usgYn));
			// 관리자 = 시스템 운영자(RL0001)
			if (enabled && Constant.ROLE_ADMIN.equals(roleIdStr)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 관리자만 "전체 길드 / 특정 길드" 조회를 허용합니다.
	 * - view_all_guilds=true 또는 view_guild_id가 세션 길드와 다르면 admin 필요
	 */
	private ResponseEntity<?> requireAdminForGuildOverride(HttpServletRequest request, Map<String, Object> param) {
		if (param == null) return null;
		Map<String, Object> userInfo = getSessUserInfo(request);
		String sessGuildId = userInfo != null && userInfo.get("sess_guild_id") != null ? String.valueOf(userInfo.get("sess_guild_id")) : null;
		
		boolean viewAll = false;
		Object viewAllObj = param.get("view_all_guilds");
		if (viewAllObj instanceof Boolean) viewAll = (Boolean) viewAllObj;
		else if (viewAllObj != null) viewAll = "true".equalsIgnoreCase(String.valueOf(viewAllObj)) || "Y".equalsIgnoreCase(String.valueOf(viewAllObj));
		
		String viewGuildId = param.get("view_guild_id") != null ? String.valueOf(param.get("view_guild_id")) : null;
		boolean wantsOtherGuild = viewGuildId != null && !viewGuildId.trim().isEmpty() && sessGuildId != null && !viewGuildId.trim().equals(sessGuildId);
		boolean wantsOverride = viewAll || wantsOtherGuild;
		if (!wantsOverride) return null;
		
		if (!isAdminUser(request)) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "관리자만 전체 길드/특정 길드 전적 조회가 가능합니다.");
			return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
		}
		return null;
	}
	
	
    @Operation(summary = "몬스터 목록 조회", description = "페이지네이션이 적용된 몬스터 목록을 조회합니다.")
    @PostMapping("/monster-list")
    public ResponseEntity<?> selectMonsterList(@RequestBody Map<String, Object> param, HttpSession session) {
    	List<Map<String, ?>> list = swService.selectMonsterList(param);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @Operation(summary = "전체 페이지 수 조회", description = "몬스터 목록의 전체 페이지 수를 조회합니다.")
    @PostMapping("/total-page-count")
    public ResponseEntity<?> selectTotalPageCount(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	ResponseEntity<?> guard = requireLoginAndGuild(request, param);
    	if (guard != null) return guard;
    	ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
    	if (adminGuard != null) return adminGuard;
    	int count = swService.selectTotalPageCount(param);
        return new ResponseEntity<>(count, HttpStatus.OK);
    }

    @Operation(summary = "적 팀 목록 조회", description = "적 팀 목록을 조회합니다.")
    @PostMapping("/enemyTeam-list")
    public ResponseEntity<?> selectEnemyTeamList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	ResponseEntity<?> guard = requireLoginAndGuild(request, param);
    	if (guard != null) return guard;
    	ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
    	if (adminGuard != null) return adminGuard;
    	List<Map<String, ?>> list = swService.selectEnemyTeamList(param);
    	
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
	

	
    @Operation(summary = "팀 정보 저장", description = "적 팀 또는 아군 팀 정보를 저장합니다.")
    @PostMapping("/enemyTeam-save")
    public ResponseEntity<?> insertEnemyTeamSave(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	ResponseEntity<?> guard = requireLoginAndGuild(request, param);
    	if (guard != null) return guard;

    	int type = (int) param.get("type");
    	int n = -1;
    	if (type == 1) {
    		n = swService.insertEnemyTeamSave(param);
    	} else if (type == 2) {
    		n = swService.insertFriendlyteamTeamSave(param);
    	}
    	
    	String result = n > -1 ? "SUCCESS" : "FAIL";
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
	
    @Operation(summary = "몬스터 기본 정보 조회", description = "특정 몬스터의 기본 정보(스탯, 스킬, 리더)를 조회합니다.")
    @PostMapping("/monster/info")
    public ResponseEntity<?> selectMonsterInfo(@RequestBody Map<String, Object> param, HttpSession session) {
    	String monsterId = param.get("monster_id") != null ? param.get("monster_id").toString() : null;
    	
    	if (monsterId == null || monsterId.isEmpty()) {
    		Map<String, Object> error = new HashMap<>();
    		error.put("error", "monster_id는 필수입니다.");
    		return ResponseEntity.badRequest().body(error);
    	}
    	
    	log.info("몬스터 기본 정보 조회 요청: monster_id={}", monsterId);
    	Map<String, ?> result = swService.selectMonsterInfo(monsterId);
    	
    	return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
    @Operation(summary = "몬스터 상세 정보 조회", description = "특정 몬스터의 상세 정보를 조회합니다.")
    @PostMapping("/monster-detail-list")
    public ResponseEntity<?> selectMonsterDetailList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	ResponseEntity<?> guard = requireLoginAndGuild(request, param);
    	if (guard != null) return guard;
    	ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
    	if (adminGuard != null) return adminGuard;
    	
    	// 공덱 이력 페이지네이션 파라미터 설정 (기본값: limit=10, offset=1)
    	if (!param.containsKey("historyLimit") || param.get("historyLimit") == null) {
    		param.put("historyLimit", 10);
    	}
    	if (!param.containsKey("historyOffset") || param.get("historyOffset") == null) {
    		param.put("historyOffset", 1);
    	}
    	// 기존 파라미터명도 지원 (하위 호환성)
    	if (param.containsKey("limit") && !param.containsKey("historyLimit")) {
    		param.put("historyLimit", param.get("limit"));
    	}
    	if (param.containsKey("offset") && !param.containsKey("historyOffset")) {
    		param.put("historyOffset", param.get("offset"));
    	}
    	
    	// 추천 공덱 페이지네이션 파라미터 설정 (기본값: limit=5, offset=1)
    	if (!param.containsKey("recommendedLimit") || param.get("recommendedLimit") == null) {
    		param.put("recommendedLimit", 5);
    	}
    	if (!param.containsKey("recommendedOffset") || param.get("recommendedOffset") == null) {
    		param.put("recommendedOffset", 1);
    	}
    	
    	Map<String, ?> list = swService.selectMonsterDetailList(param);

        return new ResponseEntity<>(list, HttpStatus.OK);
    }
	
    @Operation(summary = "길드 공성 JSON 검증", description = "길드 공성전 로그 데이터의 중복 여부를 확인합니다.")
    @SuppressWarnings("unchecked")
	@PostMapping("/siege-validate")
    public ResponseEntity<?> validateSiegeData(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	List<Map<String, ?>> log_list = (List<Map<String, ?>>) p.get("log_list");
    	List<Map<String, Object>> siegeItems = new java.util.ArrayList<>();
    	int totalBattleCount = 0;
    	
    	for (int i = 0; i < log_list.size(); i++) {
    		Map<String, ?> list = log_list.get(i);
    		List<Map<String, ?>> guild_info_list = (List<Map<String, ?>>) list.get("guild_info_list");
    		List<Map<String, ?>> battle_log_list = (List<Map<String, ?>>) list.get("battle_log_list");
    		
    		if (guild_info_list != null && guild_info_list.size() > 0) {
    			Map<String, ?> firstGuildInfo = guild_info_list.get(0);
    			int battleCount = battle_log_list != null ? battle_log_list.size() : 0;
    			totalBattleCount += battleCount;
    			
    			// 중복 체크
    			Map<String, ?> matchCheck = swService.selectGuildMatchCheck(firstGuildInfo);
    			boolean isDuplicate = !"0".equals(matchCheck.get("count").toString());
    			
    			// 3파전 길드 정보 추출 (1등, 2등, 3등)
    			List<Map<String, Object>> guilds = new java.util.ArrayList<>();
    			for (Map<String, ?> guildInfo : guild_info_list) {
    				Map<String, Object> guild = new HashMap<>();
    				guild.put("guildId", guildInfo.get("guild_id") != null ? guildInfo.get("guild_id").toString() : null);
    				guild.put("guildName", guildInfo.get("guild_name") != null ? guildInfo.get("guild_name").toString() : null);
    				guild.put("rating", guildInfo.get("rating_id") != null ? guildInfo.get("rating_id") : null);
    				guild.put("matchRank", guildInfo.get("match_rank") != null ? guildInfo.get("match_rank").toString() : null);
    				guilds.add(guild);
    			}
    			
    			Map<String, Object> siegeItem = new HashMap<>();
    			siegeItem.put("siegeId", firstGuildInfo.get("siege_id") != null ? firstGuildInfo.get("siege_id").toString() : null);
    			siegeItem.put("matchId", firstGuildInfo.get("match_id") != null ? firstGuildInfo.get("match_id").toString() : null);
    			siegeItem.put("timestamp", firstGuildInfo.get("log_timestamp") != null ? firstGuildInfo.get("log_timestamp").toString() : null);
    			siegeItem.put("battleCount", battleCount);
    			siegeItem.put("isDuplicate", isDuplicate);
    			siegeItem.put("index", i);
    			siegeItem.put("guilds", guilds); // 3파전 길드 정보
    			
    			siegeItems.add(siegeItem);
    		}
    	}
    	
    	Map<String, Object> result = new HashMap<>();
    	result.put("totalSiegeCount", log_list.size());
    	result.put("totalBattleCount", totalBattleCount);
    	result.put("siegeItems", siegeItems);
    	
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
	
    @Operation(summary = "길드 공성 JSON 저장", description = "길드 공성전 로그 데이터를 저장합니다.")
    @SuppressWarnings("unchecked")
	@PostMapping("/siege-upload")
    @Transactional
    @CacheEvict(cacheNames = {"guildSiegeHistory", "guildSiegeHistoryCount"}, allEntries = true)
    public ResponseEntity<?> saveSiegeData(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	List<Map<String, ?>> log_list = (List<Map<String, ?>>) p.get("log_list");
    	Map<String, String> siegeOptions = (Map<String, String>) p.get("siegeOptions"); // "skip" 또는 "overwrite"
    	
    	int insertedSiegeCount = 0;
    	int insertedBattleCount = 0;
    	int totalBattleCount = 0;
    	for (int i = 0; i < log_list.size(); i++) {
    		Map<String, ?> list = log_list.get(i);
    		List<Map<String, ?>> guild_info_list = (List<Map<String, ?>>) list.get("guild_info_list");
    		List<Map<String, ?>> battle_log_list = (List<Map<String, ?>>) list.get("battle_log_list");

    		// null 방어 (업로드 데이터가 비정상인 경우에도 NPE 방지)
    		final List<Map<String, ?>> safeGuildInfoList = guild_info_list == null ? java.util.Collections.emptyList() : guild_info_list;
    		final List<Map<String, ?>> safeBattleLogList = battle_log_list == null ? java.util.Collections.emptyList() : battle_log_list;
    		
    		totalBattleCount += safeBattleLogList.size();
    		
    		// siegeOptions 확인: 해당 인덱스가 "skip"이면 건너뛰기
    		if (siegeOptions != null && siegeOptions.containsKey(String.valueOf(i))) {
    			String option = siegeOptions.get(String.valueOf(i));
    			if ("skip".equals(option)) {
    				continue; // 건너뛰기
    			}
    			// "overwrite"인 경우 기존 데이터 삭제 후 저장
    			if ("overwrite".equals(option) && safeGuildInfoList.size() > 0) {
    				Map<String, ?> firstGuildInfo = safeGuildInfoList.get(0);
    				String matchId = firstGuildInfo.get("match_id") != null ? firstGuildInfo.get("match_id").toString() : null;
    				if (matchId != null) {
    					// 기존 데이터 삭제 (순서 중요: deck -> battle_log -> guild_info)
    					// 1. battle_deck 삭제
    					swService.deleteGuildSiegeBattleDeckByMatchId(matchId);
    					// 2. battle_log 삭제
    					swService.deleteGuildSiegeBattleLogByMatchId(matchId);
    					// 3. guild_info 삭제
    					swService.deleteGuildSiegeInfoByMatchId(matchId);
    				}
    			}
    		}
    		
    		boolean siegeInserted = false;
    		// overwrite 옵션이 있는지 확인
    		boolean isOverwrite = siegeOptions != null && siegeOptions.containsKey(String.valueOf(i)) && "overwrite".equals(siegeOptions.get(String.valueOf(i)));
    		
    		for (int j = 0; j < safeGuildInfoList.size(); j++) {
    			Map<String, ?> guild_info = safeGuildInfoList.get(j);
    			// overwrite가 아닌 경우에만 중복 체크
    			if (j == 0 && !isOverwrite) {
    				Map<String, ?> matchCheck = swService.selectGuildMatchCheck(guild_info);
    				if (!"0".equals(matchCheck.get("count").toString())) {
    					// 중복이고 옵션이 없으면 건너뛰기
    					if (siegeOptions == null || !siegeOptions.containsKey(String.valueOf(i))) {
    						break;
    					}
    				}
    			}
    			swService.insertGuildSiegeInfo(guild_info);
    			if (j == 0) {
    				siegeInserted = true;
    			}
    		}
    		
    		if (siegeInserted) {
    			insertedSiegeCount++;
    		}
    		
    		for (int j = 0; j < safeBattleLogList.size(); j++) {
    			Map<String, ?> battle_log = safeBattleLogList.get(j);
				Map<String, ?> matchCheck = swService.selectBattleMatchCheck(battle_log);
				if (!"0".equals(matchCheck.get("count").toString())) {
					// 중복이고 옵션이 없으면 건너뛰기
					if (siegeOptions == null || !siegeOptions.containsKey(String.valueOf(i)) || 
					    !"overwrite".equals(siegeOptions.get(String.valueOf(i)))) {
						continue;
					}
				}
        		swService.insertGuildSiegeBattleLog(battle_log);
        		insertedBattleCount++;
        		
        		List<Map<String, ?>> view_battle_deck_info = (List<Map<String, ?>>) battle_log.get("view_battle_deck_info");
        		if (view_battle_deck_info != null) {
	        		for (int k = 0; k < view_battle_deck_info.size(); k++) {
	        			List<String> view_battle_deck = (List<String>) view_battle_deck_info.get(k);
	        			Map<String, String> deckParam = new HashMap<>();
	        			deckParam.put("match_id", battle_log.get("match_id").toString());
	        			deckParam.put("log_id", battle_log.get("log_id").toString());
	        			deckParam.put("log_timestamp", battle_log.get("log_timestamp").toString());
	        			deckParam.put("monster_id_1", String.valueOf(view_battle_deck.get(0)));
	        			deckParam.put("monster_id_2", String.valueOf(view_battle_deck.get(1)));
	        			deckParam.put("monster_id_3", String.valueOf(view_battle_deck.get(2)));
	        			deckParam.put("type", k == 0 ? "attack" : "defense");
	            		swService.insertGuildSiegeBattleDeck(deckParam);
	        		}
        		}
    		}
    	}
    	
    	Map<String, Object> result = new HashMap<>();
    	result.put("totalSiegeCount", log_list.size());
    	result.put("insertedSiegeCount", insertedSiegeCount);
    	result.put("totalBattleCount", totalBattleCount);
    	result.put("insertedBattleCount", insertedBattleCount);
    	
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(summary = "최근 점령전 목록 조회", description = "최근 점령전 목록을 조회합니다. (FRONT 호환: list + totalPage)")
    @PostMapping("/recent-siege-list")
    public ResponseEntity<?> selectRecentSiegeList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;
    	ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, p);
    	if (adminGuard != null) return adminGuard;

    	int paging = 10;
    	int offset = 0;
    	
    	if (p != null) {
    		Object pagingObj = p.get("paging");
    		if (pagingObj != null) {
    			try { paging = Integer.parseInt(String.valueOf(pagingObj)); } catch (Exception ignore) {}
    		}
    		Object offsetObj = p.get("offset");
    		if (offsetObj != null) {
    			try { offset = Integer.parseInt(String.valueOf(offsetObj)); } catch (Exception ignore) {}
    		}
    		// page 기반도 지원
    		if (p.get("page") != null) {
    			try {
    				int page = Integer.parseInt(String.valueOf(p.get("page")));
    				if (page > 0) offset = (page - 1) * paging;
    			} catch (Exception ignore) {}
    		}
    	}
    	
    	Map<String, Object> q = new HashMap<>();
    	q.putAll(p);
    	q.put("limit", paging);
    	q.put("offset", offset);
    	
    	List<Map<String, ?>> list = swService.selectGuildSiegeHistorySimple(q);
    	Map<String, Object> countParam = new HashMap<>();
    	countParam.putAll(p);
    	countParam.remove("limit");
    	countParam.remove("offset");
    	countParam.remove("page");
    	countParam.remove("paging");
    	int totalCount = swService.selectGuildSiegeHistoryCount(countParam);
    	int totalPage = paging > 0 ? (int) Math.ceil(totalCount / (double) paging) : 0;
    	
    	Map<String, Object> resp = new HashMap<>();
    	resp.put("list", list);
    	resp.put("totalPage", totalPage);
    	
    	return new ResponseEntity<>(resp, HttpStatus.OK);
    }
	
    @Operation(summary = "아레나 JSON 저장", description = "아레나 대전 로그 데이터를 저장합니다.")
    @SuppressWarnings("unchecked")
	@PostMapping("/rta-upload")
    @Transactional
    public ResponseEntity<?> saveArenaData(@RequestBody Map<String, Object> param, HttpSession session) {
    	int success = 0;
    	int fail = 0;
    	List<Map<String, ?>> log_list = (List<Map<String, ?>>) param.get("arenaJson");
    	for (int i = 0; i < log_list.size(); i++) {
    		Map<String, ?> list = log_list.get(i);
			int matchCheck = swService.selectArenaKeyCheck(list);
			if (matchCheck == 0) {
				success += swService.insertArenaInfo(list);
				
				Map<Integer, Map<String, Object>> user_list = (Map<Integer, Map<String, Object>>) list.get("user_list");
		        for (Map.Entry<Integer, Map<String, Object>> entry : user_list.entrySet()) {
		            Map<String, Object> user_info = entry.getValue();
	    			user_info.put("rid", list.get("rid"));
	        		swService.insertArenaUserInfo(user_info);
	        		
	        		Map<String, Object> pick_info = (Map<String, Object>) user_info.get("pick_info");
	        		pick_info.put("rid", list.get("rid"));
	        		pick_info.put("wizard_id", user_info.get("wizard_id"));
	        		List<Integer> banList = (List<Integer>) pick_info.get("banned_slot_ids");
	        		pick_info.put("banned_slot_id", banList.get(0));
	        		
	        		swService.insertArenaPickInfo(pick_info);

	            	List<Map<String, ?>> unit_list = (List<Map<String, ?>>) pick_info.get("unit_list");
	            	for (int k = 0; k < unit_list.size(); k++) {
	            		Map<String, Object> unit = (Map<String, Object>) unit_list.get(k);
		        		unit.put("rid", list.get("rid"));
		        		unit.put("wizard_id", user_info.get("wizard_id"));
		        		swService.insertArenaUnitInfo(unit);
	            	}
	    		}
	    		
			} else {
				fail += 1;
			}
    	}
    	Map<String, Integer> result = new HashMap<>();
    	result.put("success", success);
    	result.put("fail", fail);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
	
    @Operation(summary = "전적 목록 조회", description = "전적 목록을 조회합니다.")
    @PostMapping("/record-list")
    public ResponseEntity<?> selectRecordList(@RequestBody Map<String, Object> param, HttpSession session) {
    	List<Map<String, ?>> list = swService.selectRecordList(param);
    	
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
	
    @Operation(summary = "전적 상세 조회", description = "사용자별 전적 상세 정보를 조회합니다.")
    @PostMapping("/record-detail")
    public ResponseEntity<?> selectRecordUserDetail(@RequestBody Map<String, Object> param, HttpSession session) {
    	List<Map<String, ?>> list = swService.selectRecordUserDetail(param);
    	
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
    
    @Operation(summary = "길드 공성 히스토리 조회", description = "길드 공성전 히스토리를 조회합니다.")
    @PostMapping("/guild-siege-history")
    public ResponseEntity<?> selectGuildSiegeHistory(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	// 무제한 조회 방지: limit/offset 기본값 강제
    	Map<String, Object> q = new HashMap<>();
    	if (param != null) q.putAll(param);

    	ResponseEntity<?> guard = requireLoginAndGuild(request, q);
    	if (guard != null) return guard;
    	ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, q);
    	if (adminGuard != null) return adminGuard;
    	
    	int limit = 10;
    	int offset = 0;
    	try {
    		if (q.get("limit") != null) limit = Integer.parseInt(String.valueOf(q.get("limit")));
    	} catch (Exception ignore) {}
    	try {
    		if (q.get("offset") != null) offset = Integer.parseInt(String.valueOf(q.get("offset")));
    	} catch (Exception ignore) {}
    	// page 기반이 들어오면 offset 계산
    	try {
    		if (q.get("page") != null) {
    			int page = Integer.parseInt(String.valueOf(q.get("page")));
    			if (page > 0) offset = (page - 1) * limit;
    		}
    	} catch (Exception ignore) {}
    	
    	// 과도한 limit 상한 (DB 보호)
    	if (limit <= 0) limit = 10;
    	if (limit > 100) limit = 100;
    	if (offset < 0) offset = 0;
    	
    	q.put("limit", limit);
    	q.put("offset", offset);
    	
    	List<Map<String, ?>> list = swService.selectGuildSiegeHistorySimple(q);
    	
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
    
    @Operation(summary = "길드 공성 히스토리 수 조회", description = "길드 공성전 히스토리의 총 개수를 조회합니다.")
    @PostMapping("/guild-siege-history-count")
    public ResponseEntity<?> selectGuildSiegeHistoryCount(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	// count는 limit/offset 영향 없이 집계되도록 제거
    	Map<String, Object> q = new HashMap<>();
    	if (param != null) q.putAll(param);

    	ResponseEntity<?> guard = requireLoginAndGuild(request, q);
    	if (guard != null) return guard;
    	ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, q);
    	if (adminGuard != null) return adminGuard;

    	q.remove("limit");
    	q.remove("offset");
    	q.remove("page");
    	q.remove("paging");
    	int count = swService.selectGuildSiegeHistoryCount(q);
    	
        return new ResponseEntity<>(count, HttpStatus.OK);
    }
    
    @Operation(summary = "공덱 상세 정보 조회", description = "공덱의 상세 정보를 조회합니다.")
    @PostMapping("/deck-detail")
    public ResponseEntity<?> selectDeckDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	Map<String, ?> result = swService.selectDeckDetail(param);
    	
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
    @Operation(summary = "공덱 삭제", description = "공덱을 삭제합니다.")
    @PostMapping("/deck-detail-delete")
    public ResponseEntity<?> deleteDeckDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	int n = swService.deleteDeckDetail(param);
    	
    	String result = n > 0 ? "SUCCESS" : "FAIL";
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(summary = "공덱 스탯 수정", description = "공덱 몬스터 스탯만 수정합니다. (몬스터 변경 불가)")
    @PostMapping("/deck-detail-update")
    public ResponseEntity<?> updateDeckDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	int n = swService.updateRecommendedAttackDeckStats(p);
    	String result = n > 0 ? "SUCCESS" : "FAIL";
    	return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
    @Operation(summary = "현재 시즌 조회", description = "현재 진행 중인 점령전 시즌 정보를 조회합니다.")
    @PostMapping("/current-season")
    public ResponseEntity<?> selectCurrentSeason(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	Map<String, ?> result = swService.selectCurrentSeason(param);
    	
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
}
