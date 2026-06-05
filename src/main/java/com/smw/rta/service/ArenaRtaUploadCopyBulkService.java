package com.smw.rta.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Service;

import com.smw.monster.service.ArenaRtaPersistMode;
import com.smw.rta.config.RtaRawApplyProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RTA 아레나 업로드 정규화 적재: {@code COPY FROM STDIN} → TEMP → {@code INSERT … ON CONFLICT}.
 * <p>
 * JDBC 대용량 {@code VALUES} 다중행보다 왕복·파싱 부담이 적다. 동일 트랜잭션·커넥션에서만 호출할 것.
 * 실패 시 호출부가 SAVEPOINT 롤백 후 기존 MyBatis 다중행 INSERT 로 폴백한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArenaRtaUploadCopyBulkService {

	private static final String SP = "sp_rta_arena_copy";

	private static final String CREATE_TMP_RAW = """
			CREATE TEMP TABLE tmp_arena_copy_raw (
			  rid bigint NOT NULL,
			  p_hex text NOT NULL
			) ON COMMIT DROP
			""";

	private static final String MERGE_RAW = """
			INSERT INTO ranker_rtpvp_replay_raw (rid)
			SELECT rid
			FROM tmp_arena_copy_raw
			ORDER BY rid
			ON CONFLICT (rid) DO UPDATE SET
				  inserted_at = CURRENT_TIMESTAMP
				, apply_status = 'pending'
				, applied_at = NULL
				, last_error = NULL
				, retry_count = 0
			""";

	private static final String MERGE_RAW_PAYLOAD = """
			INSERT INTO ranker_rtpvp_replay_raw_payload (rid, payload)
			SELECT rid, convert_from(decode(p_hex, 'hex'), 'UTF8')::jsonb
			FROM tmp_arena_copy_raw
			ORDER BY rid
			ON CONFLICT (rid) DO UPDATE SET
				  payload = EXCLUDED.payload
			""";

	private static final String CREATE_TMP_MATCH = """
			CREATE TEMP TABLE tmp_arena_copy_match (
			  replay_id bigint NOT NULL,
			  season_id bigint NOT NULL,
			  played_at_ms bigint NOT NULL,
			  winner_hex text NOT NULL,
			  battle_ver_hex text,
			  battle_type_hex text
			) ON COMMIT DROP
			""";

	private static final String MERGE_MATCH = """
			INSERT INTO public.rta_match (
				replay_id, season_id, played_at, winner_wizard_id, battle_version, battle_type
			)
			SELECT
				  s.replay_id
				, s.season_id
				, (TIMESTAMP WITH TIME ZONE 'epoch' + (s.played_at_ms * INTERVAL '1 millisecond'))
				, convert_from(decode(s.winner_hex, 'hex'), 'UTF8')
				, CASE WHEN s.battle_ver_hex IS NULL THEN NULL
				       ELSE convert_from(decode(s.battle_ver_hex, 'hex'), 'UTF8') END
				, CASE WHEN s.battle_type_hex IS NULL THEN NULL
				       ELSE convert_from(decode(s.battle_type_hex, 'hex'), 'UTF8') END
			FROM tmp_arena_copy_match s
			ON CONFLICT (replay_id) DO NOTHING
			""";

	private static final String CREATE_TMP_PART = """
			CREATE TEMP TABLE tmp_arena_copy_part (
			  rid bigint NOT NULL,
			  wz_hex text NOT NULL,
			  wname_hex text,
			  ch_hex text,
			  team_no smallint NOT NULL,
			  country_hex text,
			  rating_id_raw int,
			  score int,
			  rank_no int,
			  is_winner boolean,
			  fp_hex text,
			  alive_count smallint,
			  leader_pick_slot smallint
			) ON COMMIT DROP
			""";

	private static final String MERGE_PART = """
			INSERT INTO public.rta_match_participant (
				replay_id, wizard_id, season_id, played_at,
				wizard_name, channel_uid, team_side, country_code,
				rating_id, ladder_score, ladder_rank,
				is_winner, is_first_pick, alive_count, leader_pick_slot
			)
			SELECT
				  v.rid
				, convert_from(decode(v.wz_hex, 'hex'), 'UTF8')::varchar
				, m.season_id
				, m.played_at
				, convert_from(decode(v.wname_hex, 'hex'), 'UTF8')::varchar
				, convert_from(decode(v.ch_hex, 'hex'), 'UTF8')::varchar
				, v.team_no
				, CASE WHEN v.country_hex IS NULL THEN NULL
				       ELSE convert_from(decode(v.country_hex, 'hex'), 'UTF8')::varchar END
				, r.rating_id
				, v.score
				, v.rank_no
				, COALESCE(v.is_winner, false)
				, CASE
					WHEN v.fp_hex IS NULL THEN NULL::boolean
					WHEN trim(convert_from(decode(v.fp_hex, 'hex'), 'UTF8')) IN ('1','true','t','TRUE') THEN true
					WHEN trim(convert_from(decode(v.fp_hex, 'hex'), 'UTF8')) IN ('0','false','f','F','FALSE') THEN false
					ELSE NULL::boolean
				  END
				, v.alive_count
				, v.leader_pick_slot
			FROM tmp_arena_copy_part v
			JOIN public.rta_match m ON m.replay_id = v.rid
			LEFT JOIN public.rta_rating_grade r ON r.rating_id = v.rating_id_raw
			ON CONFLICT (replay_id, wizard_id) DO NOTHING
			""";

	private static final String CREATE_TMP_UNIT = """
			CREATE TEMP TABLE tmp_arena_copy_unit (
			  replay_id bigint NOT NULL,
			  wz_hex text NOT NULL,
			  pick_slot_no smallint NOT NULL,
			  um_hex text NOT NULL,
			  is_banned boolean NOT NULL
			) ON COMMIT DROP
			""";

	private static final String MERGE_UNIT = """
			INSERT INTO public.rta_match_unit_pick (
				replay_id, wizard_id, pick_slot_no, unit_master_id, is_banned
			)
			SELECT
				  u.replay_id
				, convert_from(decode(u.wz_hex, 'hex'), 'UTF8')::varchar
				, u.pick_slot_no
				, public.rta_canonical_unit_master_id(CAST(trim(convert_from(decode(u.um_hex, 'hex'), 'UTF8')) AS bigint))
				, COALESCE(u.is_banned, false)
			FROM tmp_arena_copy_unit u
			INNER JOIN public.rta_match m ON m.replay_id = u.replay_id
			ON CONFLICT (replay_id, wizard_id, pick_slot_no) DO NOTHING
			""";

	private static final String CREATE_TMP_APPLIED = """
			CREATE TEMP TABLE tmp_arena_copy_applied (rid bigint NOT NULL) ON COMMIT DROP
			""";

	private static final String UPDATE_RAW_APPLIED = """
			UPDATE ranker_rtpvp_replay_raw r SET
				  apply_status = 'applied'
				, applied_at = CURRENT_TIMESTAMP
				, last_error = NULL
			WHERE r.rid IN (SELECT rid FROM tmp_arena_copy_applied ORDER BY rid)
			""";

	private final RtaRawApplyProperties rtaRawApplyProperties;

	/**
	 * SAVEPOINT 내에서 COPY 적재. 성공 시 RELEASE, 실패 시 ROLLBACK TO SAVEPOINT 후 {@code false}.
	 */
	public boolean flushViaCopy(
			Connection conn,
			List<Map<String, Object>> rawReplayRows,
			List<Map<String, ?>> arenaRows,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> unitBatch,
			List<Long> appliedRids,
			ArenaRtaPersistMode mode) throws SQLException, IOException {
		try (Statement st = conn.createStatement()) {
			st.execute("SAVEPOINT " + SP);
		}
		try {
			if (rtaRawApplyProperties.isCopyBulkSynchronousCommitOff()) {
				try (Statement st = conn.createStatement()) {
					st.execute("SET LOCAL synchronous_commit = off");
				}
			}
			ArenaRtaPersistMode m = mode != null ? mode : ArenaRtaPersistMode.FULL;
			boolean writeRaw = m == ArenaRtaPersistMode.FULL;
			boolean writeNorm = m == ArenaRtaPersistMode.FULL || m == ArenaRtaPersistMode.NORMALIZED_ONLY;
			boolean markApplied = m == ArenaRtaPersistMode.FULL || m == ArenaRtaPersistMode.NORMALIZED_ONLY;

			PGConnection pg = RtaPgCopySupport.unwrapPg(conn);
			CopyManager cm = pg.getCopyAPI();

			if (writeRaw && rawReplayRows != null && !rawReplayRows.isEmpty()) {
				try (Statement st = conn.createStatement()) {
					st.execute(CREATE_TMP_RAW);
				}
				copyRaw(cm, rawReplayRows);
				try (Statement st = conn.createStatement()) {
					st.execute(MERGE_RAW);
					st.execute(MERGE_RAW_PAYLOAD);
				}
			}
			if (writeNorm && arenaRows != null && !arenaRows.isEmpty()) {
				try (Statement st = conn.createStatement()) {
					st.execute(CREATE_TMP_MATCH);
				}
				copyMatch(cm, arenaRows);
				try (Statement st = conn.createStatement()) {
					st.execute(MERGE_MATCH);
				}
			}
			if (writeNorm && userBatch != null && !userBatch.isEmpty()) {
				try (Statement st = conn.createStatement()) {
					st.execute(CREATE_TMP_PART);
				}
				copyParticipant(cm, userBatch);
				try (Statement st = conn.createStatement()) {
					st.execute(MERGE_PART);
				}
			}
			if (writeNorm && unitBatch != null && !unitBatch.isEmpty()) {
				try (Statement st = conn.createStatement()) {
					st.execute(CREATE_TMP_UNIT);
				}
				copyUnit(cm, unitBatch);
				try (Statement st = conn.createStatement()) {
					st.execute(MERGE_UNIT);
				}
			}
			if (markApplied && appliedRids != null && !appliedRids.isEmpty()) {
				try (Statement st = conn.createStatement()) {
					st.execute(CREATE_TMP_APPLIED);
				}
				copyAppliedRids(cm, appliedRids);
				try (Statement st = conn.createStatement()) {
					st.execute(UPDATE_RAW_APPLIED);
				}
			}

			try (Statement st = conn.createStatement()) {
				st.execute("RELEASE SAVEPOINT " + SP);
			}
			log.debug("[rta-upload] COPY 벌크 적재 완료 (raw={}, match={}, user={}, unit={}, applied={})",
					writeRaw  && rawReplayRows != null ? rawReplayRows.size() : 0,
					writeNorm && arenaRows     != null ? arenaRows.size()     : 0,
					writeNorm && userBatch     != null ? userBatch.size()     : 0,
					writeNorm && unitBatch     != null ? unitBatch.size()     : 0,
					markApplied && appliedRids != null ? appliedRids.size()   : 0);
			return true;
		} catch (SQLException | IOException | RuntimeException e) {
			try (Statement st = conn.createStatement()) {
				st.execute("ROLLBACK TO SAVEPOINT " + SP);
			} catch (SQLException se) {
				log.error("[rta-upload] SAVEPOINT 롤백 실패", se);
				throw se;
			}
			log.warn("[rta-upload] COPY 벌크 실패 — VALUES 경로로 폴백: {}", e.toString());
			return false;
		}
	}

	private static void copyRaw(CopyManager cm, List<Map<String, Object>> rows) throws IOException, SQLException {
		String copySql = "COPY tmp_arena_copy_raw (rid, p_hex) FROM STDIN WITH (FORMAT text)";
		try (InputStream in = new RtaTsvRowInputStream<>(rows, row -> {
			Long rid = (Long) row.get("rid");
			String hex = hexUtf8((String) row.get("payload"));
			return tabLine(
					rid != null ? String.valueOf(rid) : null,
					hex);
		})) {
			cm.copyIn(copySql, in);
		}
	}

	private static void copyMatch(CopyManager cm, List<Map<String, ?>> rows) throws IOException, SQLException {
		String copySql = """
				COPY tmp_arena_copy_match (replay_id, season_id, played_at_ms, winner_hex, battle_ver_hex, battle_type_hex)
				FROM STDIN WITH (FORMAT text)
				""";
		try (InputStream in = new RtaTsvRowInputStream<>(rows, row -> {
			Long rid = normalizeLong(row.get("rid"));
			Long season = normalizeLong(row.get("season_id"));
			Timestamp ts = row.get("date_add") instanceof Timestamp ? (Timestamp) row.get("date_add") : null;
			long ms = ts != null ? ts.getTime() : 0L;
			Object winWiz = row.get("winner_wizard_id");
			String winPlain = winWiz != null ? String.valueOf(winWiz).trim() : "";
			String rootPlain = row.get("wizard_id") != null ? String.valueOf(row.get("wizard_id")).trim() : "";
			String wz = hexUtf8(!winPlain.isEmpty() ? winPlain : rootPlain);
			String bv = hexNullable(row.get("battle_ver"));
			String bt = hexNullable(row.get("battle_type"));
			return tabLine(
					rid != null ? String.valueOf(rid) : null,
					season != null ? String.valueOf(season) : null,
					String.valueOf(ms),
					wz,
					bv,
					bt);
		})) {
			cm.copyIn(copySql, in);
		}
	}

	/** insertArenaInfoBulk 와 동일: winner = winner_wizard_id 있으면 그 값, 없으면 루트 wizard_id */
	private static String hexNullable(Object o) {
		if (o == null) {
			return null;
		}
		String s = String.valueOf(o).trim();
		if (s.isEmpty()) {
			return null;
		}
		return hexUtf8(s);
	}

	private static void copyParticipant(CopyManager cm, List<Map<String, ?>> rows) throws IOException, SQLException {
		String copySql = """
				COPY tmp_arena_copy_part (
				  rid, wz_hex, wname_hex, ch_hex, team_no, country_hex, rating_id_raw,
				  score, rank_no, is_winner, fp_hex, alive_count, leader_pick_slot
				) FROM STDIN WITH (FORMAT text)
				""";
		try (InputStream in = new RtaTsvRowInputStream<>(rows, row -> {
			Long rid = normalizeLong(row.get("rid"));
			String wiz = row.get("wizard_id") != null ? String.valueOf(row.get("wizard_id")).trim() : "";
			return tabLine(
					rid != null ? String.valueOf(rid) : null,
					hexUtf8(wiz),
					hexNullable(row.get("wizard_name")),
					hexNullable(row.get("channel_uid")),
					sqlSmallint(row.get("team_no")),
					hexNullable(row.get("country")),
					sqlInt(row.get("rating_id")),
					sqlInt(row.get("score")),
					sqlInt(row.get("rank_no")),
					sqlBool(row.get("is_winner")),
					hexNullable(row.get("is_first_pick")),
					sqlSmallint(row.get("alive_count")),
					sqlSmallint(row.get("leader_pick_slot")));
		})) {
			cm.copyIn(copySql, in);
		}
	}

	private static void copyUnit(CopyManager cm, List<Map<String, ?>> rows) throws IOException, SQLException {
		String copySql = """
				COPY tmp_arena_copy_unit (replay_id, wz_hex, pick_slot_no, um_hex, is_banned)
				FROM STDIN WITH (FORMAT text)
				""";
		try (InputStream in = new RtaTsvRowInputStream<>(rows, row -> {
			Long rid = normalizeLong(row.get("rid"));
			String wiz = row.get("wizard_id") != null ? String.valueOf(row.get("wizard_id")).trim() : "";
			Object um = row.get("unit_master_id");
			String umStr = um != null ? String.valueOf(um).trim() : "";
			return tabLine(
					rid != null ? String.valueOf(rid) : null,
					hexUtf8(wiz),
					sqlSmallint(row.get("pick_slot_id")),
					hexUtf8(umStr),
					sqlBool(row.get("is_banned")));
		})) {
			cm.copyIn(copySql, in);
		}
	}

	private static void copyAppliedRids(CopyManager cm, List<Long> rids) throws IOException, SQLException {
		String copySql = "COPY tmp_arena_copy_applied (rid) FROM STDIN WITH (FORMAT text)";
		try (InputStream in = new RtaTsvRowInputStream<>(rids, rid -> tabLine(rid != null ? String.valueOf(rid) : null))) {
			cm.copyIn(copySql, in);
		}
	}

	private static String sqlInt(Object o) {
		Integer n = parseIntFlexible(o);
		return n != null ? String.valueOf(n) : null;
	}

	private static String sqlSmallint(Object o) {
		Integer n = parseIntFlexible(o);
		return n != null ? String.valueOf(n) : null;
	}

	/** COPY boolean: t / f / \\N */
	private static String sqlBool(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Boolean) {
			return ((Boolean) o) ? "t" : "f";
		}
		String s = String.valueOf(o).trim();
		if (s.isEmpty()) {
			return null;
		}
		if ("1".equals(s) || "true".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s)) {
			return "t";
		}
		if ("0".equals(s) || "false".equalsIgnoreCase(s) || "f".equalsIgnoreCase(s)) {
			return "f";
		}
		return null;
	}

	private static Integer parseIntFlexible(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Number) {
			return ((Number) o).intValue();
		}
		try {
			return Integer.parseInt(o.toString().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Long normalizeLong(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Long) {
			return (Long) o;
		}
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		try {
			return Long.parseLong(String.valueOf(o).trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String hexUtf8(String s) {
		if (s == null) {
			return null;
		}
		return HexFormat.of().formatHex(s.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] tabLine(String... fields) {
		StringBuilder sb = new StringBuilder(fields.length * 16);
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				sb.append('\t');
			}
			if (fields[i] == null) {
				sb.append("\\N");
			} else {
				sb.append(fields[i]);
			}
		}
		sb.append('\n');
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}
}
