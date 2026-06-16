package com.smw.monster.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.smw.monster.dto.response.SiegeApiArchiveResponse;
import com.smw.monster.dto.response.SiegeBattleLogListResponse;
import com.smw.monster.dto.response.SiegeBattleReplayResponse;
import com.smw.monster.dto.response.SiegeMapBaseDefenseResponse;
import com.smw.monster.service.SiegeCollectorService;
import com.smw.monster.service.SiegeMapService;
import com.smw.monster.service.summonerswarService;
import com.sysconf.security.AdminPrivilegeResolver;
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

	@Autowired
	private SiegeMapService siegeMapService;

	@Autowired
	private SiegeCollectorService siegeCollectorService;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

	/** selectBattleMatchCheck 와 동일한 복합 키 (match_id 는 매치 단위로 이미 고정) */
	private static String battleDedupKey(Map<String, ?> battleOrRow) {
		Object ts = battleOrRow.get("log_timestamp");
		Object w = battleOrRow.get("wizard_id");
		Object o = battleOrRow.get("opp_wizard_id");
		return String.valueOf(ts) + "|" + String.valueOf(w) + "|" + String.valueOf(o);
	}

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

	private boolean isAdminUser(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		return userInfo != null && adminPrivilegeResolver.isAdminUser(userInfo);
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

    @Operation(summary = "방덱 수동 등록", description = "전투 로그 집계에 없어도 방덱을 시즌에 등록해 enemyTeam 목록에 표시합니다. (siege_defense_deck_manual)")
    @PostMapping("/siege-defense-deck-manual")
    public ResponseEntity<?> upsertSiegeDefenseDeckManual(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	ResponseEntity<?> guard = requireLoginAndGuild(request, param);
    	if (guard != null) return guard;

    	Object d1 = param != null ? param.get("def_monster_1") : null;
    	Object d2 = param != null ? param.get("def_monster_2") : null;
    	Object d3 = param != null ? param.get("def_monster_3") : null;
    	if (d1 == null || String.valueOf(d1).isBlank()
    			|| d2 == null || String.valueOf(d2).isBlank()
    			|| d3 == null || String.valueOf(d3).isBlank()) {
    		Map<String, Object> error = new HashMap<>();
    		error.put("result", "FAIL");
    		error.put("message", "def_monster_1, def_monster_2, def_monster_3는 필수입니다.");
    		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    	}

    	int n = swService.upsertSiegeDefenseDeckManual(param);
    	String result = n > 0 ? "SUCCESS" : "FAIL";
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

	@Operation(summary = "몬스터 상세 - 기본 정보만", description = "방덱 기본 정보(enemyData)만 조회하여 먼저 표시합니다.")
	@PostMapping("/monster-detail-basic")
	public ResponseEntity<?> selectMonsterDetailBasic(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireLoginAndGuild(request, param);
		if (guard != null) return guard;
		ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
		if (adminGuard != null) return adminGuard;
		Map<String, ?> list = swService.selectMonsterDetailBasic(param);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "몬스터 상세 - 추천 공덱만", description = "추천 공덱 목록만 조회합니다.")
	@PostMapping("/monster-detail-recommended")
	public ResponseEntity<?> selectMonsterDetailRecommended(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireLoginAndGuild(request, param);
		if (guard != null) return guard;
		ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
		if (adminGuard != null) return adminGuard;
		if (!param.containsKey("recommendedLimit") || param.get("recommendedLimit") == null) {
			param.put("recommendedLimit", 5);
		}
		if (!param.containsKey("recommendedOffset") || param.get("recommendedOffset") == null) {
			param.put("recommendedOffset", 1);
		}
		Map<String, ?> list = swService.selectMonsterDetailRecommended(param);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "몬스터 상세 - 공성률 이력만", description = "공덱 이력(historyList)만 조회합니다.")
	@PostMapping("/monster-detail-history")
	public ResponseEntity<?> selectMonsterDetailHistory(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireLoginAndGuild(request, param);
		if (guard != null) return guard;
		ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
		if (adminGuard != null) return adminGuard;
		if (!param.containsKey("historyLimit") || param.get("historyLimit") == null) {
			param.put("historyLimit", 10);
		}
		if (!param.containsKey("historyOffset") || param.get("historyOffset") == null) {
			param.put("historyOffset", 1);
		}
		Map<String, ?> list = swService.selectMonsterDetailHistory(param);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "몬스터 상세 - 최근 전적", description = "개별 전투 로그(recentBattleList)를 시간 역순으로 조회합니다.")
	@PostMapping("/monster-detail-recent-battles")
	public ResponseEntity<?> selectMonsterDetailRecentBattles(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireLoginAndGuild(request, param);
		if (guard != null) return guard;
		ResponseEntity<?> adminGuard = requireAdminForGuildOverride(request, param);
		if (adminGuard != null) return adminGuard;
		if (!param.containsKey("recentLimit") || param.get("recentLimit") == null) {
			param.put("recentLimit", 10);
		}
		if (!param.containsKey("recentOffset") || param.get("recentOffset") == null) {
			param.put("recentOffset", 1);
		}
		Map<String, ?> result = swService.selectMonsterDetailRecentBattles(param);
		return new ResponseEntity<>(result, HttpStatus.OK);
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
    @Caching(evict = {
            @CacheEvict(cacheNames = {
                    "guildSiegeHistory", "guildSiegeHistoryCount", "battleRecordList", "battleRecordDetail"
            }, cacheManager = "shortLivedCacheManager", allEntries = true),
            @CacheEvict(cacheNames = {
                    "enemyTeamList", "monsterDetailBasic", "monsterDetailRecommended", "monsterDetailHistory", "monsterDetailRecentBattles"
            }, cacheManager = "monsterDetailCacheManager", allEntries = true)
    })
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

    		List<Map<String, ?>> pendingBattles = new ArrayList<>();
    		List<Map<String, String>> pendingDecks = new ArrayList<>();
    		Set<String> existingBattleKeys = new HashSet<>();
    		Set<String> pendingBattleKeys = new HashSet<>();
    		if (!safeBattleLogList.isEmpty()) {
    			Object midObj = safeBattleLogList.get(0).get("match_id");
    			if (midObj != null) {
    				List<Map<String, ?>> existRows = swService.selectBattleLogKeysForMatch(midObj.toString());
    				if (existRows != null) {
    					for (Map<String, ?> r : existRows) {
    						existingBattleKeys.add(battleDedupKey(r));
    					}
    				}
    			}
    		}
    		for (int j = 0; j < safeBattleLogList.size(); j++) {
    			Map<String, ?> battle_log = safeBattleLogList.get(j);
    			String dedupKey = battleDedupKey(battle_log);
    			if (pendingBattleKeys.contains(dedupKey)) {
    				continue;
    			}
    			boolean dupDb = existingBattleKeys.contains(dedupKey);
    			if (dupDb) {
    				if (siegeOptions == null || !siegeOptions.containsKey(String.valueOf(i))
    						|| !"overwrite".equals(siegeOptions.get(String.valueOf(i)))) {
    					continue;
    				}
    			}
        		pendingBattles.add(battle_log);
        		pendingBattleKeys.add(dedupKey);
        		
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
	            		pendingDecks.add(deckParam);
	        		}
        		}
    		}
    		if (!pendingBattles.isEmpty()) {
    			log.info("[siege-upload] insertGuildSiegeBattleLogBatch rows={} (단건 insertGuildSiegeBattleLog 아님)", pendingBattles.size());
    			swService.insertGuildSiegeBattleLogBatch(pendingBattles);
    			insertedBattleCount += pendingBattles.size();
    		}
    		if (!pendingDecks.isEmpty()) {
    			log.info("[siege-upload] insertGuildSiegeBattleDeckBatch rows={} (단건 insertGuildSiegeBattleDeck 아님)", pendingDecks.size());
    			swService.insertGuildSiegeBattleDeckBatch(pendingDecks);
    		}
    		if (!pendingBattles.isEmpty()) {
    			// 길드별 업로드 매치 집합 → 매치들이 속한 시즌(season_no) 단위로 재집계
    			// (전투 일자→guild_siege_season 매핑: 경계월에 다른 시즌 경기가 섞이지 않도록)
    			java.util.Map<String, java.util.Set<String>> guildToMatchIds = new java.util.LinkedHashMap<>();
    			for (Map<String, ?> battle : pendingBattles) {
    				Object matchIdObj = battle.get("match_id");
    				Object guildIdObj = battle.get("guild_id");
    				if (matchIdObj == null || guildIdObj == null) {
    					continue;
    				}
    				String matchId = matchIdObj.toString().trim();
    				String guildId = guildIdObj.toString().trim();
    				if (matchId.isEmpty() || guildId.isEmpty()) {
    					continue;
    				}
    				guildToMatchIds.computeIfAbsent(guildId, k -> new java.util.LinkedHashSet<>()).add(matchId);
    			}
    			for (Map.Entry<String, java.util.Set<String>> e : guildToMatchIds.entrySet()) {
    				String guildId = e.getKey();
    				java.util.List<Integer> seasonNos = swService.selectAffectedSeasonNos(guildId, e.getValue());
    				for (Integer seasonNo : seasonNos) {
    					if (seasonNo != null) {
    						swService.refreshSiegeDefenseDeckStatsForGuildSeasonNo(guildId, seasonNo);
    					}
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

	@Operation(summary = "점령전 지도 거점 방덱", description = "GetGuildSiegeBaseDefenseUnitList 스냅샷 + Matchup 거점 상태.")
	@PostMapping("/siege-map-base-defense")
	public ResponseEntity<?> siegeMapBaseDefense(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		Map<String, Object> p = param != null ? param : new HashMap<>();
		ResponseEntity<?> guard = requireLoginAndGuild(request, p);
		if (guard != null) {
			return guard;
		}
		String matchId = p.get("match_id") != null ? String.valueOf(p.get("match_id")) : null;
		if (matchId == null || matchId.isBlank()) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "match_id가 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
		int baseNumber;
		try {
			baseNumber = Integer.parseInt(String.valueOf(p.get("base_number")));
		} catch (NumberFormatException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "base_number 형식이 올바르지 않습니다.");
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
		Long snapshotId = null;
		if (p.get("snapshot_id") != null) {
			try {
				snapshotId = Long.parseLong(String.valueOf(p.get("snapshot_id")));
			} catch (NumberFormatException e) {
				Map<String, Object> body = new HashMap<>();
				body.put("result", "FAIL");
				body.put("message", "snapshot_id 형식이 올바르지 않습니다.");
				return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
			}
		}
		try {
			SiegeMapBaseDefenseResponse body = siegeMapService.getBaseDefense(matchId, baseNumber, snapshotId);
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
	}

	private void applyCollectorGuildScope(HttpServletRequest request, Map<String, Object> param) {
		boolean viewAll = false;
		Object viewAllObj = param.get("view_all_guilds");
		if (viewAllObj instanceof Boolean b) {
			viewAll = b;
		} else if (viewAllObj != null) {
			viewAll = "true".equalsIgnoreCase(String.valueOf(viewAllObj)) || "Y".equalsIgnoreCase(String.valueOf(viewAllObj));
		}
		if (viewAll && isAdminUser(request)) {
			param.put("view_all_guilds", true);
		}
	}

	@Operation(summary = "매치 전투 로그(수집기)", description = "battle_log_list — battle_desc·replay_rid_ref.")
	@PostMapping("/siege-collector-battle-log-list")
	public ResponseEntity<?> siegeCollectorBattleLogList(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		Map<String, Object> p = param != null ? param : new HashMap<>();
		ResponseEntity<?> guard = requireLoginAndGuild(request, p);
		if (guard != null) {
			return guard;
		}
		applyCollectorGuildScope(request, p);
		try {
			return ResponseEntity.ok(siegeCollectorService.getMatchBattleLogs(p));
		} catch (IllegalArgumentException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
	}

	@Operation(summary = "전투 리플레이(수집기)", description = "siege_battle_replay_payload 조회.")
	@PostMapping("/siege-collector-battle-replay")
	public ResponseEntity<?> siegeCollectorBattleReplay(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		Map<String, Object> p = param != null ? param : new HashMap<>();
		ResponseEntity<?> guard = requireLoginAndGuild(request, p);
		if (guard != null) {
			return guard;
		}
		applyCollectorGuildScope(request, p);
		long rid;
		try {
			rid = Long.parseLong(String.valueOf(p.get("rid")));
		} catch (NumberFormatException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "rid 형식이 올바르지 않습니다.");
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
		try {
			SiegeBattleReplayResponse body = siegeCollectorService.getBattleReplay(rid, p);
			if (body == null) {
				Map<String, Object> fail = new HashMap<>();
				fail.put("result", "FAIL");
				fail.put("message", "리플레이를 찾을 수 없습니다.");
				return new ResponseEntity<>(fail, HttpStatus.NOT_FOUND);
			}
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
	}

	@Operation(summary = "API 아카이브 최신(수집기)", description = "siege_collector_api_archive command별 최신 1건.")
	@PostMapping("/siege-collector-api-archive-latest")
	public ResponseEntity<?> siegeCollectorApiArchiveLatest(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		Map<String, Object> p = param != null ? param : new HashMap<>();
		ResponseEntity<?> guard = requireLoginAndGuild(request, p);
		if (guard != null) {
			return guard;
		}
		applyCollectorGuildScope(request, p);
		try {
			SiegeApiArchiveResponse body = siegeCollectorService.getLatestApiArchive(p);
			if (body == null) {
				Map<String, Object> fail = new HashMap<>();
				fail.put("result", "FAIL");
				fail.put("message", "아카이브 데이터가 없습니다.");
				return new ResponseEntity<>(fail, HttpStatus.NOT_FOUND);
			}
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
	}
	
    @Operation(summary = "아레나 JSON 저장", description = "아레나 대전 로그 데이터를 저장합니다.")
    @SuppressWarnings("unchecked")
	@PostMapping("/rta-upload")
    public ResponseEntity<?> saveArenaData(@RequestBody Map<String, Object> param, HttpSession session) {
    	List<Map<String, ?>> log_list = (List<Map<String, ?>>) param.get("arenaJson");
    	if (log_list == null || log_list.isEmpty()) {
    		Map<String, Integer> empty = new HashMap<>();
    		empty.put("success", 0);
    		empty.put("fail", 0);
    		return new ResponseEntity<>(empty, HttpStatus.OK);
    	}
    	Map<String, Integer> result = swService.applyArenaRtaUploadFromParsedItems(log_list);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
	
    @Operation(summary = "시즌 목록 조회", description = "점령전 시즌 목록을 조회합니다. (전적 시즌 드롭다운용)")
    @PostMapping("/season-list")
    public ResponseEntity<?> selectSeasonList(@RequestBody Map<String, Object> param) {
    	List<Map<String, ?>> list = swService.selectSeasonList(param != null ? param : new HashMap<>());
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @Operation(summary = "전적 목록 조회", description = "로그인한 사용자의 길드 인원 전적만 조회합니다.")
    @PostMapping("/record-list")
    public ResponseEntity<?> selectRecordList(@RequestBody Map<String, Object> param, HttpServletRequest request) {
    	Map<String, Object> q = new HashMap<>();
    	if (param != null) q.putAll(param);
    	ResponseEntity<?> guard = requireLoginAndGuild(request, q);
    	if (guard != null) return guard;
    	List<Map<String, ?>> list = swService.selectRecordList(q);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
	
    @Operation(summary = "전적 상세 조회", description = "사용자별 전적 상세 정보를 조회합니다. (본인 길드 인원만)")
    @PostMapping("/record-detail")
    public ResponseEntity<?> selectRecordUserDetail(@RequestBody Map<String, Object> param, HttpServletRequest request) {
    	Map<String, Object> q = new HashMap<>();
    	if (param != null) q.putAll(param);
    	ResponseEntity<?> guard = requireLoginAndGuild(request, q);
    	if (guard != null) return guard;
    	List<Map<String, ?>> list = swService.selectRecordUserDetail(q);
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
    
    @Operation(summary = "룬 세트 마스터 목록", description = "추천 공덱 룬 선택용 마스터 목록을 조회합니다.")
    @PostMapping("/rune-master/list")
    public ResponseEntity<?> selectRuneMasterList(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	return new ResponseEntity<>(swService.selectRuneMasterList(), HttpStatus.OK);
    }

    @Operation(summary = "공덱 조합 목록", description = "source=RECOMMENDED(추천 공덱) 또는 RECORD(전적 공덱) 공격 3몬스터 조합 목록을 조회합니다.")
    @PostMapping("/popular-attack-decks")
    public ResponseEntity<?> selectPopularAttackDecks(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;
    	Map<String, ?> result = swService.selectPopularAttackDeckCombos(p);
    	return new ResponseEntity<>(result, HttpStatus.OK);
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

    @Operation(summary = "공덱 추천/비추천", description = "vote: UP, DOWN, CLEAR — 방덱(def)+등록 공덱(deck_id) 또는 방덱+공격(atk) 자유 투표")
    @PostMapping("/deck-vote")
    public ResponseEntity<?> setDeckVote(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
    	Map<String, Object> p = param != null ? param : new HashMap<>();
    	ResponseEntity<?> guard = requireLoginAndGuild(request, p);
    	if (guard != null) return guard;

    	Object d1 = p.get("def_monster_1") != null ? p.get("def_monster_1") : p.get("defMonster1");
    	Object d2 = p.get("def_monster_2") != null ? p.get("def_monster_2") : p.get("defMonster2");
    	Object d3 = p.get("def_monster_3") != null ? p.get("def_monster_3") : p.get("defMonster3");
    	if (d1 == null || String.valueOf(d1).trim().isEmpty()
    			|| d2 == null || String.valueOf(d2).trim().isEmpty()
    			|| d3 == null || String.valueOf(d3).trim().isEmpty()) {
    		Map<String, Object> body = new HashMap<>();
    		body.put("result", "FAIL");
    		body.put("message", "방덱(수비) def_monster_1, def_monster_2, def_monster_3이 필요합니다.");
    		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    	}
    	p.put("def_monster_1", d1);
    	p.put("def_monster_2", d2);
    	p.put("def_monster_3", d3);

    	Object deckIdObj = p.get("deck_id");
    	String deckIdStr = deckIdObj != null ? String.valueOf(deckIdObj).trim() : "";
    	Object a1 = p.get("atk_monster_1") != null ? p.get("atk_monster_1") : p.get("atkMonster1");
    	Object a2 = p.get("atk_monster_2") != null ? p.get("atk_monster_2") : p.get("atkMonster2");
    	Object a3 = p.get("atk_monster_3") != null ? p.get("atk_monster_3") : p.get("atkMonster3");
    	if (a1 != null) {
    		p.put("atk_monster_1", a1);
    	}
    	if (a2 != null) {
    		p.put("atk_monster_2", a2);
    	}
    	if (a3 != null) {
    		p.put("atk_monster_3", a3);
    	}
    	if (deckIdStr.isEmpty() || "0".equals(deckIdStr)) {
    		boolean atkOk = a1 != null && !String.valueOf(a1).trim().isEmpty()
    				&& a2 != null && !String.valueOf(a2).trim().isEmpty()
    				&& a3 != null && !String.valueOf(a3).trim().isEmpty();
    		if (!atkOk) {
    			Map<String, Object> body = new HashMap<>();
    			body.put("result", "FAIL");
    			body.put("message", "deck_id가 없으면 atk_monster_1, atk_monster_2, atk_monster_3이 필요합니다.");
    			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    		}
    	}
    	try {
    		swService.setDeckVote(p);
    		Map<String, Object> ok = new HashMap<>();
    		ok.put("result", "SUCCESS");
    		return new ResponseEntity<>(ok, HttpStatus.OK);
    	} catch (IllegalArgumentException e) {
    		Map<String, Object> body = new HashMap<>();
    		body.put("result", "FAIL");
    		body.put("message", e.getMessage());
    		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    	}
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
