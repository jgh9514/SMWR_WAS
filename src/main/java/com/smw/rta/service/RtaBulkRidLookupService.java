package com.smw.rta.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.smw.rta.model.RtaSynergyBanDeltaRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대량 replay_id(rid) 집합에 대한 DB 존재 여부 조회.
 * <p>
 * IN 청킹·긴 {@code IN (?,?,…)} 대신 <strong>COPY → TEMP({@code tmp_bulk_rids}) → JOIN</strong> 로
 * 단일 왕복 처리한다(동일 조인 형태로 {@code unnest(:bigint[]) AS t(rid)} 를 쓰는 것과 유사·인덱스 친화).
 * 시너지 집계 prefetch({@link #prefetchSynergyLookupMaps})·소량 완료 표시는 MyBatis
 * {@code unnest(ARRAY[…]::bigint[])} 경로({@code markSynergyAggDoneForRids})와 병행한다.
 * <ul>
 *   <li>전용 커넥션으로 동작 — 호출부 트랜잭션/커넥션과 완전히 독립.</li>
 *   <li>임시 테이블({@code tmp_bulk_rids})은 {@code ON COMMIT DROP}이므로 커넥션 반환 시 자동 삭제.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtaBulkRidLookupService {

	private final DataSource dataSource;

	/**
	 * rids 중 {@code rta_match.replay_id}에 이미 존재하는 rid 반환.
	 */
	public Set<Long> selectExistingReplayIds(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return Collections.emptySet();
		}
		long t0 = System.currentTimeMillis();
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			RtaBulkRidTempTable.loadRids(conn, rids);
			Set<Long> result = new HashSet<>();
			try (Statement st = conn.createStatement();
				 ResultSet rs = st.executeQuery(
						"SELECT m.replay_id " +
						"FROM public.rta_match m " +
						"JOIN tmp_bulk_rids t ON t.rid = m.replay_id")) {
				while (rs.next()) {
					result.add(rs.getLong(1));
				}
			}
			conn.rollback();
			log.debug("[rta-bulk-rid] selectExistingReplayIds rids={} found={} {}ms",
					rids.size(), result.size(), System.currentTimeMillis() - t0);
			return result;
		} catch (Exception e) {
			log.error("[rta-bulk-rid] selectExistingReplayIds 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("selectExistingReplayIds via temp table failed", e);
		}
	}

	/**
	 * rids 기준 {@code rta_match_participant}에 존재하는 {@code (replay_id|wizard_id)} PK 키 집합 반환.
	 * <p>
	 * 키 포맷은 서비스 레이어의 {@code arenaUserPkKeyString} 과 동일: {@code rid + "|" + wizard_id}.
	 */
	public Set<String> selectExistingUserPkKeys(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return Collections.emptySet();
		}
		long t0 = System.currentTimeMillis();
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			RtaBulkRidTempTable.loadRids(conn, rids);
			Set<String> result = new HashSet<>();
			try (Statement st = conn.createStatement();
				 ResultSet rs = st.executeQuery(
						"SELECT p.replay_id::text || '|' || p.wizard_id " +
						"FROM public.rta_match_participant p " +
						"JOIN tmp_bulk_rids t ON t.rid = p.replay_id")) {
				while (rs.next()) {
					String pk = rs.getString(1);
					if (pk != null) {
						result.add(pk);
					}
				}
			}
			conn.rollback();
			log.debug("[rta-bulk-rid] selectExistingUserPkKeys rids={} found={} {}ms",
					rids.size(), result.size(), System.currentTimeMillis() - t0);
			return result;
		} catch (Exception e) {
			log.error("[rta-bulk-rid] selectExistingUserPkKeys 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("selectExistingUserPkKeys via temp table failed", e);
		}
	}

	/**
	 * 시너지 집계용: {@code rta_match}·{@code rta_match_unit_pick}·{@code rta_match_participant} 를
	 * {@code tmp_bulk_rids} 와 조인해 한 커넥션에서 채운다.
	 * <p>
	 * 기존 {@code replay_id = ANY(bigint[])} + ForkJoin 병렬 3쿼리 대비, 대량 rid 에서 플래너·왕복이 유리하다.
	 */
	public void prefetchSynergyLookupMaps(Collection<Long> rids, Map<Long, Map<String, Object>> replayByRid,
			Map<Long, List<Map<String, Object>>> unitsByRid, Map<Long, List<Map<String, Object>>> ratingsByRid) {
		if (rids == null || rids.isEmpty()) {
			return;
		}
		long t0 = System.currentTimeMillis();
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			RtaBulkRidTempTable.loadRids(conn, rids);
			loadSynergyReplayRows(conn, replayByRid);
			loadSynergyUnitRows(conn, unitsByRid);
			loadSynergyRatingRows(conn, ratingsByRid);
			conn.rollback();
			log.info("[rta-bulk-rid] synergy prefetch JOIN rids={} {}ms", rids.size(), System.currentTimeMillis() - t0);
		} catch (Exception e) {
			log.error("[rta-bulk-rid] synergy prefetch 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("prefetchSynergyLookupMaps failed", e);
		}
	}

	/**
	 * 시즌×티어×콤보(원본 몬스터 ID 문자열)별 벤 건수 —
	 * 시너지 픽 UPSERT 직후 {@code rta_agg_synergy_solo.ban_cnt} 갱신용.
	 */
	/**
	 * {@code rta_match} 부모 없이 unit / participant 만 남은 행 삭제 — COPY→{@code tmp_bulk_rids}→JOIN (독립 커넥션·커밋).
	 */
	public int deleteOrphanArenaChildrenByRids(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return 0;
		}
		long t0 = System.currentTimeMillis();
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			int n = RtaBulkRidTempTable.deleteArenaOrphanChildrenByRids(conn, rids);
			conn.commit();
			log.debug("[rta-bulk-rid] deleteOrphanArenaChildrenByRids rids={} deletedRows={} {}ms",
					rids.size(), n, System.currentTimeMillis() - t0);
			return n;
		} catch (Exception e) {
			log.error("[rta-bulk-rid] deleteOrphanArenaChildrenByRids 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("deleteOrphanArenaChildrenByRids failed", e);
		}
	}

	public List<RtaSynergyBanDeltaRow> aggregateSynergyBanIncrements(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return Collections.emptyList();
		}
		long t0 = System.currentTimeMillis();
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			RtaBulkRidTempTable.loadRids(conn, rids);
			List<RtaSynergyBanDeltaRow> out = new ArrayList<>();
			String sql = """
					SELECT m.season_id,
					       p.rating_id,
					       COALESCE(mcm.original_monster_id, u.unit_master_id::text) AS combo_unit_key,
					       COUNT(*)::bigint AS delta
					FROM public.rta_match_unit_pick u
					INNER JOIN public.rta_match m ON m.replay_id = u.replay_id
					INNER JOIN public.rta_match_participant p
					        ON p.replay_id = u.replay_id
					       AND p.wizard_id = u.wizard_id
					LEFT JOIN public.monster_collaboration_mapping mcm
					       ON mcm.collaboration_monster_id = u.unit_master_id::text
					JOIN tmp_bulk_rids t ON t.rid = u.replay_id
					WHERE COALESCE(u.is_banned, false) = true
					  AND p.rating_id IS NOT NULL
					  AND p.rating_id > 0
					GROUP BY m.season_id, p.rating_id,
					         COALESCE(mcm.original_monster_id, u.unit_master_id::text)
					""";
			try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					String key = rs.getString("combo_unit_key");
					if (key != null) {
						out.add(new RtaSynergyBanDeltaRow(
								rs.getLong("season_id"),
								rs.getInt("rating_id"),
								key,
								rs.getLong("delta")));
					}
				}
			}
			conn.rollback();
			log.debug("[rta-bulk-rid] synergy ban increments rids={} keys={} {}ms", rids.size(), out.size(),
					System.currentTimeMillis() - t0);
			return out;
		} catch (Exception e) {
			log.error("[rta-bulk-rid] aggregateSynergyBanIncrements 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("aggregateSynergyBanIncrements failed", e);
		}
	}

	private static void loadSynergyReplayRows(Connection conn, Map<Long, Map<String, Object>> replayByRid)
			throws SQLException {
		String sql = """
				SELECT m.replay_id AS rid,
				       m.winner_wizard_id AS winner_wizard_id,
				       m.played_at AS date_add,
				       m.season_id
				FROM public.rta_match m
				JOIN tmp_bulk_rids t ON t.rid = m.replay_id
				""";
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				long rid = rs.getLong("rid");
				Map<String, Object> row = new HashMap<>(8);
				row.put("rid", rid);
				row.put("winner_wizard_id", rs.getString("winner_wizard_id"));
				row.put("date_add", rs.getTimestamp("date_add"));
				row.put("season_id", rs.getObject("season_id"));
				replayByRid.put(rid, row);
			}
		}
	}

	private static void loadSynergyUnitRows(Connection conn, Map<Long, List<Map<String, Object>>> unitsByRid)
			throws SQLException {
		String sql = """
				SELECT DISTINCT u.replay_id AS rid,
				       u.wizard_id AS wizard_id,
				       u.unit_master_id AS unit_master_id
				FROM public.rta_match_unit_pick u
				JOIN tmp_bulk_rids t ON t.rid = u.replay_id
				WHERE COALESCE(u.is_banned, false) = false
				""";
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				long rid = rs.getLong("rid");
				Map<String, Object> row = new HashMap<>(8);
				row.put("rid", rid);
				row.put("wizard_id", rs.getString("wizard_id"));
				row.put("unit_master_id", rs.getObject("unit_master_id"));
				unitsByRid.computeIfAbsent(rid, k -> new ArrayList<>()).add(row);
			}
		}
	}

	private static void loadSynergyRatingRows(Connection conn, Map<Long, List<Map<String, Object>>> ratingsByRid)
			throws SQLException {
		String sql = """
				SELECT mp.replay_id AS rid,
				       mp.wizard_id AS wizard_id,
				       CAST(mp.rating_id AS INTEGER) AS rating_id
				FROM public.rta_match_participant mp
				JOIN tmp_bulk_rids t ON t.rid = mp.replay_id
				""";
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				long rid = rs.getLong("rid");
				Map<String, Object> row = new HashMap<>(8);
				row.put("rid", rid);
				row.put("wizard_id", rs.getString("wizard_id"));
				row.put("rating_id", rs.getObject("rating_id"));
				ratingsByRid.computeIfAbsent(rid, k -> new ArrayList<>()).add(row);
			}
		}
	}
}
