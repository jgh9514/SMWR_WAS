package com.smw.monster.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smw.monster.dto.request.SiegeMapSnapshotUploadRequest;
import com.smw.monster.dto.response.SiegeMapBaseDefenseDeckResponse;
import com.smw.monster.dto.response.SiegeMapBaseDefenseResponse;
import com.smw.monster.dto.response.SiegeMapBaseDefenseUnitResponse;
import com.smw.monster.dto.response.SiegeMapBaseImageItemResponse;
import com.smw.monster.dto.response.SiegeMapBaseLayoutItemResponse;
import com.smw.monster.dto.response.SiegeMapLayoutMasterResponse;
import com.smw.monster.mapper.SiegeMapMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiegeMapServiceImpl implements SiegeMapService {

	private final SiegeMapMapper siegeMapMapper;

	@Override
	@Transactional
	public Map<String, Object> ingestMatchupSnapshot(SiegeMapSnapshotUploadRequest request) {
		Map<String, Object> matchup = request != null ? request.getMatchup() : null;
		if (matchup == null || matchup.isEmpty()) {
			throw new IllegalArgumentException("matchup 본문이 비어 있습니다.");
		}
		Object ret = matchup.get("ret_code");
		if (ret != null && !"0".equals(String.valueOf(ret))) {
			throw new IllegalArgumentException("matchup ret_code가 0이 아닙니다.");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> matchInfo = (Map<String, Object>) matchup.get("match_info");
		if (matchInfo == null || matchInfo.get("match_id") == null) {
			throw new IllegalArgumentException("match_info.match_id가 없습니다.");
		}

		String matchId = String.valueOf(matchInfo.get("match_id"));
		String siegeId = stringVal(matchInfo.get("siege_id"));
		String seasonYyyymm = matchId.length() >= 6 ? matchId.substring(0, 6) : matchId;
		long capturedAt = longVal(matchup.get("tvalue"));
		if (capturedAt <= 0) {
			capturedAt = longVal(matchup.get("tvaluelocal"));
		}
		if (capturedAt <= 0) {
			capturedAt = System.currentTimeMillis() / 1000L;
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> setup = (Map<String, Object>) matchup.get("setup_values");

		Map<String, Object> matchRow = new HashMap<>();
		matchRow.put("match_id", matchId);
		matchRow.put("siege_id", siegeId != null ? siegeId : "");
		matchRow.put("season_yyyymm", seasonYyyymm);
		matchRow.put("rating_id", stringVal(matchInfo.get("rating_id")));
		matchRow.put("match_type", intVal(matchInfo.get("match_type")));
		matchRow.put("match_start_time", longVal(matchInfo.get("match_start_time")));
		matchRow.put("match_finish_time", longVal(matchInfo.get("match_finish_time")));
		matchRow.put("captured_at", capturedAt);
		siegeMapMapper.upsertSiegeMapMatch(matchRow);

		Map<String, Object> snapRow = new HashMap<>();
		snapRow.put("match_id", matchId);
		snapRow.put("captured_at", capturedAt);
		snapRow.put("war_rest_start_time", setup != null ? longVal(setup.get("war_rest_start_time")) : null);
		snapRow.put("war_rest_finish_time", setup != null ? longVal(setup.get("war_rest_finish_time")) : null);
		snapRow.put("max_match_score", setup != null ? intVal(setup.get("max_match_score")) : null);
		snapRow.put("max_deck_count_per_member", setup != null ? intVal(setup.get("max_deck_count_per_member")) : null);
		snapRow.put("max_attack_unit_count", setup != null ? intVal(setup.get("max_attack_unit_count")) : null);
		snapRow.put("source", request.getSource() != null && !request.getSource().isBlank() ? request.getSource() : "db_ingest");

		siegeMapMapper.insertSiegeMapSnapshot(snapRow);
		long snapshotId = longVal(snapRow.get("id"));
		boolean inserted = snapshotId > 0;
		if (!inserted) {
			Long existing = siegeMapMapper.selectSnapshotIdByMatchAndCaptured(matchId, capturedAt);
			if (existing == null) {
				throw new IllegalStateException("스냅샷 저장에 실패했습니다.");
			}
			snapshotId = existing;
		} else {
			siegeMapMapper.incrementSnapshotCount(matchId, capturedAt);
		}

		if (inserted) {
			List<Map<String, Object>> guildRows = buildGuildRows(matchup, snapshotId);
			if (!guildRows.isEmpty()) {
				siegeMapMapper.insertSiegeMapSnapshotGuildBatch(guildRows);
			}
			List<Map<String, Object>> baseRows = buildBaseRows(matchup, snapshotId);
			if (!baseRows.isEmpty()) {
				siegeMapMapper.insertSiegeMapSnapshotBaseBatch(baseRows);
			}
		}

		Map<String, Object> result = new HashMap<>();
		result.put("matchId", matchId);
		result.put("snapshotId", snapshotId);
		result.put("capturedAt", capturedAt);
		result.put("inserted", inserted);
		log.info("[siege-map] ingest matchId={} snapshotId={} inserted={}", matchId, snapshotId, inserted);
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> getMapView(String matchId, Long snapshotId, String myGuildId) {
		if (matchId == null || matchId.isBlank()) {
			throw new IllegalArgumentException("match_id가 필요합니다.");
		}
		Map<String, ?> match = siegeMapMapper.selectSiegeMapMatchById(matchId);
		if (match == null) {
			return null;
		}
		Map<String, ?> header;
		if (snapshotId != null && snapshotId > 0) {
			header = siegeMapMapper.selectSnapshotHeaderById(snapshotId);
		} else {
			header = siegeMapMapper.selectLatestSnapshotHeader(matchId);
		}
		if (header == null) {
			Map<String, Object> empty = new HashMap<>();
			empty.put("match", match);
			empty.put("snapshot", null);
			empty.put("guilds", List.of());
			empty.put("bases", List.of());
			return empty;
		}
		long sid = longVal(header.get("id"));
		Map<String, Object> result = new HashMap<>();
		result.put("match", match);
		result.put("snapshot", header);
		result.put("guilds", enrichGuilds(siegeMapMapper.selectSnapshotGuilds(sid), myGuildId));
		result.put("bases", enrichBases(siegeMapMapper.selectSnapshotBases(sid), longVal(header.get("captured_at"))));
		return result;
	}

	private List<Map<String, Object>> enrichGuilds(List<Map<String, ?>> guilds, String myGuildId) {
		if (guilds == null || guilds.isEmpty()) return List.of();
		// 내 길드 → pos_id=1, 나머지는 기존 pos_id 오름차순으로 2/3 재배정
		List<Map<String, ?>> others = guilds.stream()
				.filter(g -> !String.valueOf(g.get("guild_id")).equals(myGuildId))
				.sorted(java.util.Comparator.comparingInt(g -> intVal(g.get("pos_id"))))
				.toList();
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, ?> g : guilds) {
			Map<String, Object> row = new HashMap<>(g);
			if (myGuildId != null && String.valueOf(g.get("guild_id")).equals(myGuildId)) {
				row.put("pos_id", 1);
			} else {
				int idx = others.indexOf(g);
				row.put("pos_id", idx + 2);
			}
			result.add(row);
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, ?>> getMatchHistory(Map<String, Object> param) {
		return siegeMapMapper.selectSiegeMapMatchHistory(param);
	}

	@Override
	@Transactional(readOnly = true)
	public int getMatchHistoryCount(Map<String, Object> param) {
		return siegeMapMapper.selectSiegeMapMatchHistoryCount(param);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, ?>> getSnapshotTimeline(String matchId) {
		return siegeMapMapper.selectSnapshotTimeline(matchId);
	}

	@Override
	@Transactional(readOnly = true)
	public SiegeMapBaseDefenseResponse getBaseDefense(String matchId, int baseNumber, Long snapshotId) {
		if (matchId == null || matchId.isBlank()) {
			throw new IllegalArgumentException("match_id가 필요합니다.");
		}
		if (baseNumber < 1 || baseNumber > 39) {
			throw new IllegalArgumentException("base_number는 1~39입니다.");
		}

		Map<String, ?> header;
		if (snapshotId != null && snapshotId > 0) {
			header = siegeMapMapper.selectSnapshotHeaderById(snapshotId);
		} else {
			header = siegeMapMapper.selectLatestSnapshotHeader(matchId);
		}
		long matchupCapturedAt = header != null ? longVal(header.get("captured_at")) : 0L;
		long sid = header != null ? longVal(header.get("id")) : 0L;

		Map<String, ?> baseRow = sid > 0 ? siegeMapMapper.selectSnapshotBase(sid, baseNumber) : null;
		Integer baseStatus = baseRow != null ? intVal(baseRow.get("base_status")) : null;
		String baseGuildId = baseRow != null ? stringVal(baseRow.get("guild_id")) : null;
		Integer remainSec = null;
		if (baseRow != null && baseStatus != null && baseStatus == 1 && matchupCapturedAt > 0) {
			long battleStart = longVal(baseRow.get("battle_start_time"));
			if (battleStart > matchupCapturedAt) {
				remainSec = (int) (battleStart - matchupCapturedAt);
			}
		}

		Map<String, Object> capParam = new HashMap<>();
		capParam.put("match_id", matchId);
		capParam.put("base_number", baseNumber);
		if (matchupCapturedAt > 0) {
			capParam.put("captured_at_max", matchupCapturedAt);
		}
		Map<String, ?> capture = siegeMapMapper.selectLatestBaseDefenseCapture(capParam);
		if (capture == null) {
			return SiegeMapBaseDefenseResponse.builder()
					.matchId(matchId)
					.baseNumber(baseNumber)
					.captureId(null)
					.capturedAt(null)
					.baseStatus(baseStatus)
					.guildId(baseGuildId)
					.remainSec(remainSec)
					.decks(List.of())
					.build();
		}

		long captureId = longVal(capture.get("id"));
		long defenseCapturedAt = longVal(capture.get("captured_at"));
		List<Map<String, ?>> deckRows = siegeMapMapper.selectBaseDefenseDecks(captureId, baseNumber);
		List<Map<String, ?>> unitRows = siegeMapMapper.selectBaseDefenseUnits(captureId);

		Map<Long, List<SiegeMapBaseDefenseUnitResponse>> unitsByDeck = new LinkedHashMap<>();
		for (Map<String, ?> u : unitRows) {
			long deckId = longVal(u.get("deck_id"));
			unitsByDeck.computeIfAbsent(deckId, k -> new ArrayList<>()).add(
					SiegeMapBaseDefenseUnitResponse.builder()
							.posId(intVal(u.get("pos_id")))
							.unitMasterId(intVal(u.get("unit_master_id")))
							.unitLevel(intVal(u.get("unit_level")))
							.krName(stringVal(u.get("kr_name")))
							.imageUrl(stringVal(u.get("image_url")))
							.build());
		}

		List<SiegeMapBaseDefenseDeckResponse> decks = new ArrayList<>(deckRows.size());
		for (Map<String, ?> d : deckRows) {
			long deckId = longVal(d.get("deck_id"));
			decks.add(SiegeMapBaseDefenseDeckResponse.builder()
					.deckId(deckId)
					.wizardId(stringVal(d.get("wizard_id")))
					.wizardName(stringVal(d.get("wizard_name")))
					.wizardLevel(intVal(d.get("wizard_level")) > 0 ? intVal(d.get("wizard_level")) : null)
					.guildId(stringVal(d.get("guild_id")))
					.deckStatus(intVal(d.get("deck_status")))
					.winCount(intVal(d.get("win_count")))
					.loseCount(intVal(d.get("lose_count")))
					.drawCount(intVal(d.get("draw_count")))
					.totalCount(intVal(d.get("total_count")))
					.winningRate(decimalVal(d.get("winning_rate")))
					.attackWizardId(stringVal(d.get("attack_wizard_id")))
					.battleStartTime(longVal(d.get("battle_start_time")) > 0 ? longVal(d.get("battle_start_time")) : null)
					.units(unitsByDeck.getOrDefault(deckId, List.of()))
					.build());
		}

		return SiegeMapBaseDefenseResponse.builder()
				.matchId(matchId)
				.baseNumber(baseNumber)
				.captureId(captureId)
				.capturedAt(defenseCapturedAt)
				.baseStatus(baseStatus)
				.guildId(baseGuildId)
				.remainSec(remainSec)
				.decks(decks)
				.build();
	}

	private List<Map<String, ?>> enrichBases(List<Map<String, ?>> bases, long capturedAt) {
		if (bases == null || bases.isEmpty()) {
			return List.of();
		}
		List<Map<String, ?>> out = new ArrayList<>(bases.size());
		for (Map<String, ?> b : bases) {
			Map<String, Object> row = new HashMap<>(b);
			int status = intVal(b.get("base_status"));
			long battleStart = longVal(b.get("battle_start_time"));
			Integer remainSec = null;
			if (status == 1 && battleStart > capturedAt) {
				remainSec = (int) (battleStart - capturedAt);
			}
			row.put("remain_sec", remainSec);
			out.add(row);
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> buildGuildRows(Map<String, Object> matchup, long snapshotId) {
		Object list = matchup.get("guild_list");
		if (!(list instanceof List<?> guildList)) {
			return List.of();
		}
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Object item : guildList) {
			if (!(item instanceof Map<?, ?> g)) {
				continue;
			}
			Map<String, Object> row = new HashMap<>();
			row.put("snapshot_id", snapshotId);
			row.put("guild_id", stringVal(g.get("guild_id")));
			row.put("pos_id", intVal(g.get("pos_id")));
			row.put("guild_name", stringVal(g.get("guild_name")));
			row.put("match_score", decimalVal(g.get("match_score")));
			row.put("match_score_increment", decimalVal(g.get("match_score_increment")));
			row.put("match_rank", intVal(g.get("match_rank")));
			row.put("play_member_count", intVal(g.get("play_member_count")));
			row.put("attack_count", intVal(g.get("attack_count")));
			row.put("attack_unit_count", intVal(g.get("attack_unit_count")));
			row.put("disqualified", intVal(g.get("disqualified")));
			rows.add(row);
		}
		return rows;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> buildBaseRows(Map<String, Object> matchup, long snapshotId) {
		Object list = matchup.get("base_list");
		if (!(list instanceof List<?> baseList)) {
			return List.of();
		}
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Object item : baseList) {
			if (!(item instanceof Map<?, ?> b)) {
				continue;
			}
			Map<String, Object> row = new HashMap<>();
			row.put("snapshot_id", snapshotId);
			row.put("base_number", intVal(b.get("base_number")));
			row.put("base_type", intVal(b.get("base_type")));
			row.put("guild_id", stringVal(b.get("guild_id")));
			row.put("base_status", intVal(b.get("base_status")));
			row.put("battle_start_time", longVal(b.get("battle_start_time")));
			row.put("construct_time", longVal(b.get("construct_time")));
			rows.add(row);
		}
		return rows;
	}

	private static String stringVal(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static int intVal(Object o) {
		if (o == null) {
			return 0;
		}
		if (o instanceof Number n) {
			return n.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(o));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static long longVal(Object o) {
		if (o == null) {
			return 0L;
		}
		if (o instanceof Number n) {
			return n.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(o));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static BigDecimal decimalVal(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof BigDecimal bd) {
			return bd;
		}
		if (o instanceof Number n) {
			return BigDecimal.valueOf(n.doubleValue());
		}
		try {
			return new BigDecimal(String.valueOf(o));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	@Transactional(readOnly = true)
	public SiegeMapLayoutMasterResponse getLayoutMaster() {
		List<SiegeMapBaseLayoutItemResponse> layouts = new ArrayList<>();
		for (Map<String, ?> row : siegeMapMapper.selectSiegeMapBaseLayoutMaster()) {
			layouts.add(SiegeMapBaseLayoutItemResponse.builder()
					.gameBaseNumber(intVal(row.get("game_base_number")))
					.castleZone(stringVal(row.get("castle_zone")))
					.slotNo(intVal(row.get("slot_no")))
					.posXPct(decimalVal(row.get("pos_x_pct")))
					.posYPct(decimalVal(row.get("pos_y_pct")))
					.ringKind(stringVal(row.get("ring_kind")))
					.build());
		}
		List<SiegeMapBaseImageItemResponse> images = new ArrayList<>();
		for (Map<String, ?> row : siegeMapMapper.selectSiegeMapBaseImageMaster()) {
			String ringKind = stringVal(row.get("ring_kind"));
			images.add(SiegeMapBaseImageItemResponse.builder()
					.castleZone(stringVal(row.get("castle_zone")))
					.ringKind(ringKind)
					.baseStatus("base".equals(ringKind) ? null : intVal(row.get("base_status")))
					.imagePath(stringVal(row.get("image_path")))
					.displayWidthPx(intVal(row.get("display_width_px")))
					.displayHeightPx(intVal(row.get("display_height_px")))
					.build());
		}
		return SiegeMapLayoutMasterResponse.builder()
				.layouts(layouts)
				.images(images)
				.build();
	}
}
