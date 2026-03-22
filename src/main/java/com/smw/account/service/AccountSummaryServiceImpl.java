package com.smw.account.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.account.mapper.AccountSummaryMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class AccountSummaryServiceImpl implements AccountSummaryService {

	private static final int BULK_INSERT_CHUNK_SIZE = 500;

	@Autowired
	private AccountSummaryMapper mapper;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	@Transactional
	@Caching(evict = {
			@CacheEvict(cacheNames = "accountSummaryLatest", allEntries = true, cacheManager = "shortLivedCacheManager"),
			@CacheEvict(cacheNames = "accountSummaryImportList", allEntries = true, cacheManager = "shortLivedCacheManager"),
			@CacheEvict(cacheNames = "accountSummaryImportDetail", allEntries = true, cacheManager = "shortLivedCacheManager"),
			@CacheEvict(cacheNames = "accountSummaryMonsterList", allEntries = true, cacheManager = "shortLivedCacheManager"),
			@CacheEvict(cacheNames = "accountSummaryMonsterCatalog", allEntries = true, cacheManager = "shortLivedCacheManager"),
			@CacheEvict(cacheNames = "accountSummaryRuneList", allEntries = true, cacheManager = "shortLivedCacheManager"),
			@CacheEvict(cacheNames = "accountSummaryRuneScoreSummary", allEntries = true, cacheManager = "shortLivedCacheManager")
	})
	public Map<String, Object> uploadAndSave(MultipartFile jsonFile, String sessUserId) throws Exception {
		if (jsonFile == null) {
			throw new IllegalArgumentException("json_file이 없습니다.");
		}
		if (sessUserId == null || sessUserId.isEmpty()) {
			throw new IllegalArgumentException("로그인이 필요합니다. (sess_user_id 없음)");
		}
		log.info("[AccountSummary] uploadAndSave start - fileName={}, size={}",
				jsonFile != null ? jsonFile.getOriginalFilename() : null,
				jsonFile != null ? jsonFile.getSize() : null);
		// 1) JSON 파싱
		String rawText = new String(jsonFile.getBytes(), StandardCharsets.UTF_8);
		JsonNode root = objectMapper.readTree(rawText);

		ParsedSwex parsed = parseSwex(root);
		log.info("[AccountSummary] parsed - wizardId={}, unitCount={}, runeCount={}",
				parsed.wizardId, parsed.unitCount, parsed.runeCount);

		// 2) 임포트 저장 (raw jsonb 포함)
		Map<String, Object> importParam = new HashMap<>();
		// 인터셉터 주입에 의존하지 않고, 보호 API에서는 명시적으로 sess_user_id를 세팅한다.
		importParam.put("sess_user_id", sessUserId);
		importParam.put("source_filename", jsonFile.getOriginalFilename());
		importParam.put("wizard_id", parsed.wizardId);
		importParam.put("wizard_name", parsed.wizardName);
		importParam.put("server_id", parsed.serverId);
		importParam.put("unit_count", parsed.unitCount);
		importParam.put("rune_count", parsed.runeCount);
		importParam.put("raw_json", rawText);

		try {
			mapper.insertImport(importParam);
		} catch (Exception e) {
			log.error("[AccountSummary] insertImport 실패 - paramKeys={}", importParam.keySet(), e);
			throw e;
		}
		Object importIdObj = importParam.get("import_id");
		Long importId = importIdObj != null ? Long.valueOf(importIdObj.toString()) : null;
		if (importId == null) {
			throw new IllegalStateException("import_id 생성에 실패했습니다.");
		}
		log.info("[AccountSummary] insertImport ok - importId={}", importId);

		// 3) 몬스터/룬 저장 (chunk insert)
		if (!parsed.monsters.isEmpty()) {
			try {
				bulkInsertMonsters(importId, parsed.monsters);
			} catch (Exception e) {
				log.error("[AccountSummary] bulkInsertMonsters 실패 - importId={}, size={}", importId, parsed.monsters.size(), e);
				throw e;
			}
		}
		if (!parsed.runes.isEmpty()) {
			try {
				bulkInsertRunes(importId, parsed.runes);
			} catch (Exception e) {
				log.error("[AccountSummary] bulkInsertRunes 실패 - importId={}, size={}", importId, parsed.runes.size(), e);
				throw e;
			}
		}

		// 4) 결과 요약 반환
		Map<String, Object> result = new HashMap<>();
		result.put("import_id", importId);
		result.put("source_filename", jsonFile.getOriginalFilename());
		result.put("wizard_id", parsed.wizardId);
		result.put("wizard_name", parsed.wizardName);
		result.put("server_id", parsed.serverId);
		result.put("unit_count", parsed.unitCount);
		result.put("rune_count", parsed.runeCount);
		log.info("[AccountSummary] uploadAndSave done - importId={}", importId);
		return result;
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryLatest", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public Map<String, Object> selectLatestImport(Map<String, Object> param) {
		Map<String, ?> latest = mapper.selectLatestImport(param);
		Map<String, Object> result = new HashMap<>();
		if (latest == null) {
			result.put("hasData", false);
			return result;
		}
		result.put("hasData", true);
		result.put("import", latest);
		return result;
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryImportList", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public List<Map<String, ?>> selectImportList(Map<String, Object> param) {
		return mapper.selectImportList(param);
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryImportDetail", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public Map<String, Object> selectImportDetail(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		Map<String, ?> detail = mapper.selectImportDetail(param);
		if (detail == null) {
			result.put("hasData", false);
			return result;
		}
		result.put("hasData", true);
		result.put("import", detail);
		return result;
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryMonsterList", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public Map<String, Object> selectMonsterList(Map<String, Object> param) {
		ensurePagingDefaults(param);
		ensureImportId(param);

		List<Map<String, ?>> items = mapper.selectMonsterList(param);
		int total = mapper.selectMonsterListCount(param);

		Map<String, Object> result = new HashMap<>();
		result.put("items", items);
		result.put("total", total);
		return result;
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryMonsterCatalog", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public Map<String, Object> selectMonsterCatalog(Map<String, Object> param) {
		ensurePagingDefaults(param);
		ensureImportId(param);

		List<Map<String, ?>> items = mapper.selectMonsterCatalog(param);
		int total = mapper.selectMonsterCatalogCount(param);

		Map<String, Object> result = new HashMap<>();
		result.put("items", items);
		result.put("total", total);
		return result;
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryRuneList", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public Map<String, Object> selectRuneList(Map<String, Object> param) {
		ensurePagingDefaults(param);
		ensureImportId(param);

		List<Map<String, ?>> items = mapper.selectRuneList(param);
		int total = mapper.selectRuneListCount(param);

		Map<String, Object> result = new HashMap<>();
		result.put("items", items);
		result.put("total", total);
		return result;
	}

	@Override
	@Cacheable(cacheNames = "accountSummaryRuneScoreSummary", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public Map<String, Object> selectRuneScoreSummary(Map<String, Object> param) {
		ensureImportId(param);

		Map<String, Object> result = new HashMap<>();
		Object importIdObj = param.get("import_id");
		result.put("import_id", importIdObj);

		List<Map<String, ?>> runes = mapper.selectRunesForScoreSummary(param);
		if (runes == null || runes.isEmpty()) {
			result.put("hasData", false);
			return result;
		}

		// Set ID
		final int SET_SWIFT = 3;   // 신속
		final int SET_DESPAIR = 10; // 절망
		final int SET_VIOLENT = 13; // 폭주
		final int SET_REVENGE = 18; // 반격
		final int SET_WILL = 16;   // 의지

		// 점수 계산 후 카테고리별 분류
		java.util.Map<String, java.util.List<Double>> byCategory = new java.util.HashMap<>();
		java.util.Map<Integer, java.util.List<Double>> bySet = new java.util.HashMap<>();

		for (Map<String, ?> r : runes) {
			Integer setId = toInt(r.get("set_id"));
			// SWRT 방식: Efficiency% * 1.8
			Double score = computeGeneralRuneScore(r.get("substats_json"), r.get("prefix_eff_json"));
			if (score == null) score = 0.0;

			// set별
			if (setId != null) {
				bySet.computeIfAbsent(setId, k -> new java.util.ArrayList<>()).add(score);
			}

			// general 카테고리별
			String cat = toGeneralCategory(setId, SET_SWIFT, SET_VIOLENT, SET_DESPAIR, SET_WILL, SET_REVENGE);
			byCategory.computeIfAbsent(cat, k -> new java.util.ArrayList<>()).add(score);
		}

		// Top 10 Average (룬 기준): 신속/폭주/절망 각각 상위 10개 룬 평균
		Map<String, Object> top10 = new HashMap<>();
		top10.put("swift", buildTopNAvg(bySet.get(SET_SWIFT), 10));
		top10.put("violent", buildTopNAvg(bySet.get(SET_VIOLENT), 10));
		top10.put("despair", buildTopNAvg(bySet.get(SET_DESPAIR), 10));

		// General avg: 신속/폭주/절망/의지/반격/기타
		Map<String, Object> general = new HashMap<>();
		general.put("swift", buildAvg(byCategory.get("swift")));
		general.put("violent", buildAvg(byCategory.get("violent")));
		general.put("despair", buildAvg(byCategory.get("despair")));
		general.put("will", buildAvg(byCategory.get("will")));
		general.put("revenge", buildAvg(byCategory.get("revenge")));
		general.put("others", buildAvg(byCategory.get("others")));

		result.put("hasData", true);
		result.put("top10", top10);
		result.put("general", general);
		result.put("scoreFormula", "Efficiency(%)=(1+Σ(sub/prefix)/max)/2.8*100, RuneScore=Efficiency*1.8");
		return result;
	}

	private Map<String, Object> buildTopNAvg(java.util.List<Double> scores, int n) {
		Map<String, Object> out = new HashMap<>();
		if (scores == null || scores.isEmpty()) {
			out.put("count", 0);
			out.put("considered", 0);
			out.put("sum", 0.0);
			out.put("avg", 0.0);
			return out;
		}
		scores.sort(java.util.Comparator.reverseOrder());
		int considered = Math.min(n, scores.size());
		double sum = 0.0;
		for (int i = 0; i < considered; i++) sum += scores.get(i);
		out.put("count", scores.size());
		out.put("considered", considered);
		out.put("sum", round2(sum));
		out.put("avg", round2(sum / considered));
		return out;
	}

	private Map<String, Object> buildAvg(java.util.List<Double> scores) {
		Map<String, Object> out = new HashMap<>();
		if (scores == null || scores.isEmpty()) {
			out.put("count", 0);
			out.put("sum", 0.0);
			out.put("avg", 0.0);
			return out;
		}
		double sum = 0.0;
		for (Double s : scores) sum += (s != null ? s : 0.0);
		out.put("count", scores.size());
		out.put("sum", round2(sum));
		out.put("avg", round2(sum / scores.size()));
		return out;
	}

	private String toGeneralCategory(Integer setId, int swift, int violent, int despair, int will, int revenge) {
		if (setId == null) return "others";
		if (setId == swift) return "swift";
		if (setId == violent) return "violent";
		if (setId == despair) return "despair";
		if (setId == will) return "will";
		if (setId == revenge) return "revenge";
		return "others";
	}

	/**
	 * SWRT(Barion) 방식 룬 효율(%)
	 * Efficiency(%) = (1 + Σ(현재 수치 / maxRoll)) / 2.8 * 100
	 * - Σ에는 sec_eff의 "총 수치" 포함
	 * - prefix_eff가 있으면 Σ에 포함
	 */
	private double computeEfficiencyPercent(Object substatsObj, Object prefixObj) {
		double sum = 0.0;
		sum += computeEffArrayRatioSum(substatsObj);
		sum += computePrefixRatioSum(prefixObj);
		double eff = (1.0 + sum) / 2.8 * 100.0;
		return eff < 0 ? 0.0 : eff;
	}

	/**
	 * General Rune Score = Efficiency(%) * 1.8
	 */
	private Double computeGeneralRuneScore(Object substatsObj, Object prefixObj) {
		double eff = computeEfficiencyPercent(substatsObj, prefixObj);
		return eff * 1.8;
	}

	private double computePrefixRatioSum(Object prefixObj) {
		if (prefixObj == null) return 0.0;
		try {
			com.fasterxml.jackson.databind.JsonNode node = toJsonNode(prefixObj);
			if (node == null || !node.isArray() || node.size() < 2) return 0.0;
			int effId = node.get(0).asInt();
			double val = node.get(1).asDouble();
			Double max = maxRollForEffect(effId);
			if (max == null || max.doubleValue() <= 0) return 0.0;
			return val / max.doubleValue();
		} catch (Exception e) {
			return 0.0;
		}
	}

	private double computeEffArrayRatioSum(Object substatsObj) {
		if (substatsObj == null) return 0.0;
		try {
			com.fasterxml.jackson.databind.JsonNode node = toJsonNode(substatsObj);
			if (node == null || !node.isArray()) return 0.0;
			double sum = 0.0;
			for (com.fasterxml.jackson.databind.JsonNode eff : node) {
				if (eff == null || !eff.isArray() || eff.size() < 2) continue;
				int effId = eff.get(0).asInt();
				double val = eff.get(1).asDouble(); // 총 수치(기본+연마)
				Double max = maxRollForEffect(effId);
				if (max == null || max.doubleValue() <= 0) continue;
				sum += val / max.doubleValue();
			}
			return sum;
		} catch (Exception e) {
			return 0.0;
		}
	}

	private com.fasterxml.jackson.databind.JsonNode toJsonNode(Object obj) throws Exception {
		if (obj == null) return null;
		if (obj instanceof com.fasterxml.jackson.databind.JsonNode) return (com.fasterxml.jackson.databind.JsonNode) obj;
		// PostgreSQL jsonb는 MyBatis에서 PGobject로 넘어오는 경우가 많음
		// (이 경우 valueToTree를 하면 배열/객체가 아니라 PGobject 구조가 직렬화되어 점수가 0으로 계산됨)
		if (obj instanceof org.postgresql.util.PGobject) {
			String v = ((org.postgresql.util.PGobject) obj).getValue();
			if (v == null || v.isEmpty()) return null;
			return objectMapper.readTree(v);
		}
		if (obj instanceof String) {
			String s = (String) obj;
			if (s == null || s.isEmpty()) return null;
			return objectMapper.readTree(s);
		}
		// jsonb가 Map/List로 들어오는 경우
		return objectMapper.valueToTree(obj);
	}

	private Double maxRollForEffect(int effId) {
		// 가이드 기준(6성 전설 max roll)
		// 8: HP%, ATK%, DEF%, ACC, RES  -> effId: 2,4,6,10,12
		// 6: SPD, CR                   -> effId: 11,9
		// 7: CD                        -> effId: 8
		switch (effId) {
			case 2:
			case 4:
			case 6:
			case 10:
			case 12:
				return 8.0;
			case 11:
			case 9:
				return 6.0;
			case 8:
				return 7.0;
			default:
				return null; // +스탯(1,3,5) 등은 가이드에 max roll이 없으므로 효율 계산에서 제외
		}
	}

	private Integer toInt(Object obj) {
		if (obj == null) return null;
		if (obj instanceof Number) return ((Number) obj).intValue();
		try {
			String s = obj.toString();
			if (s == null || s.isEmpty()) return null;
			return Integer.valueOf(s);
		} catch (Exception e) {
			return null;
		}
	}

	private double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	private void ensurePagingDefaults(Map<String, Object> param) {
		if (!param.containsKey("limit") || param.get("limit") == null) {
			param.put("limit", 50);
		}
		if (!param.containsKey("offset") || param.get("offset") == null) {
			param.put("offset", 0);
		}
	}

	private void ensureImportId(Map<String, Object> param) {
		// import_id가 없으면 최신 임포트를 조회해서 세팅
		if (!param.containsKey("import_id") || param.get("import_id") == null || "".equals(param.get("import_id"))) {
			Map<String, ?> latest = mapper.selectLatestImport(param);
			if (latest != null && latest.get("import_id") != null) {
				param.put("import_id", latest.get("import_id"));
			}
		}
	}

	private void bulkInsertMonsters(Long importId, List<Map<String, Object>> monsters) {
		for (int i = 0; i < monsters.size(); i += BULK_INSERT_CHUNK_SIZE) {
			int end = Math.min(i + BULK_INSERT_CHUNK_SIZE, monsters.size());
			List<Map<String, Object>> chunk = monsters.subList(i, end);
			for (Map<String, Object> m : chunk) {
				m.put("import_id", importId);
			}
			mapper.insertMonstersBulk(chunk);
		}
	}

	private void bulkInsertRunes(Long importId, List<Map<String, Object>> runes) {
		for (int i = 0; i < runes.size(); i += BULK_INSERT_CHUNK_SIZE) {
			int end = Math.min(i + BULK_INSERT_CHUNK_SIZE, runes.size());
			List<Map<String, Object>> chunk = runes.subList(i, end);
			for (Map<String, Object> r : chunk) {
				r.put("import_id", importId);
			}
			mapper.insertRunesBulk(chunk);
		}
	}

	private ParsedSwex parseSwex(JsonNode root) {
		ParsedSwex parsed = new ParsedSwex();

		JsonNode wizardInfo = root.path("wizard_info");
		parsed.wizardId = asLongOrNull(wizardInfo, "wizard_id");
		parsed.wizardName = asTextOrNull(wizardInfo, "wizard_name");
		parsed.serverId = asIntOrNull(wizardInfo, "server_id");

		// 몬스터 (unit_list + unit_storage_list)
		// SWEX JSON은 창고 몬스터가 unit_storage_list로 내려오는 경우가 있어 둘 다 카운트/저장한다.
		parsed.unitCount += processUnitArray(root.path("unit_list"), parsed);
		parsed.unitCount += processUnitArray(root.path("unit_storage_list"), parsed);

		// 룬 (top-level rune_list/runes가 "룬 자체 스탯"이 가장 풍부한 경우가 많아 우선 처리)
		// unit.runes는 보조 정보로 merge
		java.util.LinkedHashMap<Long, Map<String, Object>> runeMap = new java.util.LinkedHashMap<>();

		List<JsonNode> topRuneCandidates = new ArrayList<>();
		topRuneCandidates.add(root.path("rune_list"));
		topRuneCandidates.add(root.path("runes"));

		for (JsonNode runeArray : topRuneCandidates) {
			if (runeArray != null && runeArray.isArray()) {
				for (JsonNode rune : runeArray) {
					Long unitId = asLongOrNull(rune, "occupied_id");
					if (unitId == null) {
						unitId = asLongOrNull(rune, "unit_id");
					}
					Map<String, Object> r = extractRune(rune, unitId);
					if (r != null && r.get("rune_id") != null) {
						Long runeId = Long.valueOf(r.get("rune_id").toString());
						runeMap.put(runeId, r); // top-level 우선
					}
				}
			}
		}

		// unit.runes merge (없던 rune_id만 보완하거나, unit_id가 null이면 채움)
		for (Map<String, Object> r : parsed.runes) {
			if (r == null || r.get("rune_id") == null) continue;
			Long runeId = Long.valueOf(r.get("rune_id").toString());
			if (!runeMap.containsKey(runeId)) {
				runeMap.put(runeId, r);
			} else {
				Map<String, Object> existing = runeMap.get(runeId);
				// unit_id가 비어있으면 채움
				if (existing.get("unit_id") == null && r.get("unit_id") != null) {
					existing.put("unit_id", r.get("unit_id"));
				}
			}
		}

		parsed.runes.clear();
		parsed.runes.addAll(runeMap.values());
		parsed.runeCount = parsed.runes.size();
		return parsed;
	}

	private int processUnitArray(JsonNode unitArray, ParsedSwex parsed) {
		if (unitArray == null || !unitArray.isArray()) {
			return 0;
		}

		int count = unitArray.size();
		for (JsonNode unit : unitArray) {
			Long unitId = asLongOrNull(unit, "unit_id");
			Integer masterId = asIntOrNull(unit, "unit_master_id");
			Integer level = firstInt(unit, "unit_level", "level");
			Integer stars = firstInt(unit, "class", "unit_class", "stars");
			Integer attribute = firstInt(unit, "attribute", "unit_attribute");
			Integer awakenLevel = firstInt(unit, "awaken_level", "awakening");
			Integer isAwakened = firstInt(unit, "is_awakened");

			if (unitId != null) {
				Map<String, Object> m = new HashMap<>();
				m.put("unit_id", unitId);
				m.put("master_id", masterId);
				m.put("level", level);
				m.put("stars", stars);
				m.put("attribute", attribute);
				m.put("awaken_level", awakenLevel);
				m.put("is_awakened", isAwakened);
				parsed.monsters.add(m);
			}

			// 유닛에 장착된 룬
			JsonNode unitRunes = unit.path("runes");
			if (unitRunes != null && unitRunes.isArray()) {
				for (JsonNode rune : unitRunes) {
					Map<String, Object> r = extractRune(rune, unitId);
					if (r != null) {
						parsed.runes.add(r);
					}
				}
			}
		}
		return count;
	}

	private Map<String, Object> extractRune(JsonNode rune, Long unitId) {
		if (rune == null || rune.isMissingNode() || rune.isNull()) {
			return null;
		}

		Long runeId = asLongOrNull(rune, "rune_id");
		if (runeId == null) {
			runeId = asLongOrNull(rune, "id");
		}
		if (runeId == null) {
			return null;
		}

		Integer slot = firstInt(rune, "slot_no", "slot");
		Integer setId = firstInt(rune, "set_id", "set");
		// SWEX: class(성급), rank(태생등급), upgrade_curr(강화), extra(현재등급)
		Integer grade = firstInt(rune, "class", "grade", "rune_grade");
		Integer rank = firstInt(rune, "rank");
		Integer level = firstInt(rune, "upgrade_curr", "level");
		Integer extra = firstInt(rune, "extra");
		Integer baseValue = firstInt(rune, "base_value");
		Integer sellValue = firstInt(rune, "sell_value");
		Long wizardId = asLongOrNull(rune, "wizard_id");
		Integer occupiedType = asIntOrNull(rune, "occupied_type");
		Long occupiedId = asLongOrNull(rune, "occupied_id");

		// SWEX 룬 stats: pri_eff [type,value], prefix_eff [type,value], sec_eff [[type,value,grind,ench],...]
		Integer mainType = null;
		Integer mainValue = null;
		JsonNode priEff = rune.path("pri_eff");
		if (priEff != null && priEff.isArray() && priEff.size() >= 2) {
			mainType = priEff.get(0).isNumber() ? priEff.get(0).asInt() : null;
			mainValue = priEff.get(1).isNumber() ? priEff.get(1).asInt() : null;
		}
		String priEffJson = (priEff != null && priEff.isArray()) ? priEff.toString() : null;

		Integer innateType = null;
		Integer innateValue = null;
		JsonNode prefixEff = rune.path("prefix_eff");
		if (prefixEff != null && prefixEff.isArray() && prefixEff.size() >= 2) {
			innateType = prefixEff.get(0).isNumber() ? prefixEff.get(0).asInt() : null;
			innateValue = prefixEff.get(1).isNumber() ? prefixEff.get(1).asInt() : null;
		}
		String prefixEffJson = (prefixEff != null && prefixEff.isArray()) ? prefixEff.toString() : null;

		JsonNode secEff = rune.path("sec_eff");
		String substatsJson = (secEff != null && !secEff.isMissingNode() && !secEff.isNull()) ? secEff.toString() : null;

		Map<String, Object> r = new HashMap<>();
		r.put("rune_id", runeId);
		r.put("wizard_id", wizardId);
		r.put("occupied_type", occupiedType);
		r.put("occupied_id", occupiedId);
		r.put("unit_id", unitId);
		r.put("slot", slot);
		r.put("set_id", setId);
		r.put("grade", grade);
		r.put("level", level);
		r.put("rank", rank);
		r.put("extra", extra);
		r.put("base_value", baseValue);
		r.put("sell_value", sellValue);
		r.put("main_stat_type", mainType);
		r.put("main_stat_value", mainValue);
		r.put("innate_stat_type", innateType);
		r.put("innate_stat_value", innateValue);
		r.put("pri_eff_json", priEffJson);
		r.put("prefix_eff_json", prefixEffJson);
		r.put("substats_json", substatsJson);
		r.put("raw_json", rune.toString());
		return r;
	}

	private Long asLongOrNull(JsonNode node, String field) {
		if (node == null) return null;
		JsonNode v = node.get(field);
		if (v == null || v.isNull() || v.isMissingNode()) return null;
		if (v.isNumber()) return v.asLong();
		String s = v.asText(null);
		if (s == null || s.isEmpty()) return null;
		try {
			return Long.valueOf(s);
		} catch (Exception e) {
			return null;
		}
	}

	private Integer asIntOrNull(JsonNode node, String field) {
		if (node == null) return null;
		JsonNode v = node.get(field);
		if (v == null || v.isNull() || v.isMissingNode()) return null;
		if (v.isNumber()) return v.asInt();
		String s = v.asText(null);
		if (s == null || s.isEmpty()) return null;
		try {
			return Integer.valueOf(s);
		} catch (Exception e) {
			return null;
		}
	}

	private String asTextOrNull(JsonNode node, String field) {
		if (node == null) return null;
		JsonNode v = node.get(field);
		if (v == null || v.isNull() || v.isMissingNode()) return null;
		String s = v.asText(null);
		return (s == null || s.isEmpty()) ? null : s;
	}

	private Integer firstInt(JsonNode node, String... fields) {
		if (node == null) return null;
		for (String f : fields) {
			Integer v = asIntOrNull(node, f);
			if (v != null) return v;
		}
		return null;
	}

	private static class ParsedSwex {
		Long wizardId;
		String wizardName;
		Integer serverId;
		int unitCount;
		int runeCount;
		final List<Map<String, Object>> monsters = new ArrayList<>();
		final List<Map<String, Object>> runes = new ArrayList<>();
	}
}


