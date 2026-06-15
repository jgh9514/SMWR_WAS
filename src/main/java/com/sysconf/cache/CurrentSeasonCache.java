package com.sysconf.cache;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 현재 점령전 시즌 캐시.
 * guild_siege_season 서브쿼리 반복 실행을 피하고 조회 성능을 높이기 위해 사용.
 *
 * <p>시즌 경계가 같은 달력월을 공유할 수 있어(예: 시즌20 ~03-20 / 시즌21 03-30~ → 둘 다 202603),
 * 월(YYYYMM) 비교로는 시즌을 정확히 가를 수 없다. 따라서 season_no 및 KST 기준 epoch 경계를 함께 제공한다.
 */
@Slf4j
@Component
public class CurrentSeasonCache {

	private static final long TTL_MS = 5 * 60 * 1000; // 5분

	/** end_date NULL(진행 중) 시즌의 종료 epoch 상한 sentinel */
	public static final long OPEN_END_EPOCH = 99999999999L; // ~5138년

	private final JdbcTemplate jdbcTemplate;
	private volatile Snapshot cached;
	private final AtomicLong cachedAt = new AtomicLong(0);

	public CurrentSeasonCache(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 현재 시즌 스냅샷.
	 * @param seasonNo        guild_siege_season.season_no (없으면 null)
	 * @param yyyymm          시즌 시작월 YYYYMM (없으면 "000000")
	 * @param startEpoch      시즌 시작일 00:00(KST) epoch 초
	 * @param endEpochExcl    시즌 종료일+1 00:00(KST) epoch 초 (진행 중이면 OPEN_END_EPOCH)
	 */
	public record Snapshot(Integer seasonNo, String yyyymm, long startEpoch, long endEpochExcl) {}

	private Snapshot load() {
		long now = System.currentTimeMillis();
		Snapshot c = cached;
		if (c != null && (now - cachedAt.get()) < TTL_MS) {
			return c;
		}
		synchronized (this) {
			c = cached;
			if (c != null && (now - cachedAt.get()) < TTL_MS) {
				return c;
			}
			try {
				Snapshot loaded = querySnapshot();
				cached = loaded;
				cachedAt.set(System.currentTimeMillis());
				return loaded;
			} catch (Exception e) {
				log.warn("현재 시즌 조회 실패, 기본값 사용", e);
				Snapshot fallback = new Snapshot(null, "000000", 0L, OPEN_END_EPOCH);
				cached = fallback;
				cachedAt.set(now);
				return fallback;
			}
		}
	}

	private Snapshot querySnapshot() {
		// 활성 시즌(오늘이 [start_date, end_date] 안) → 없으면 최신 season_no 폴백
		String sql =
			"SELECT season_no"
			+ "     , TO_CHAR(DATE_TRUNC('month', start_date), 'YYYYMM') AS yyyymm"
			+ "     , EXTRACT(EPOCH FROM (start_date::timestamp AT TIME ZONE 'Asia/Seoul'))::bigint AS start_epoch"
			+ "     , CASE WHEN end_date IS NULL THEN " + OPEN_END_EPOCH
			+ "            ELSE EXTRACT(EPOCH FROM ((end_date + 1)::timestamp AT TIME ZONE 'Asia/Seoul'))::bigint END AS end_epoch_excl"
			+ "  FROM guild_siege_season"
			+ " WHERE start_date <= CURRENT_DATE AND (end_date IS NULL OR end_date >= CURRENT_DATE)"
			+ " ORDER BY season_no DESC LIMIT 1";
		Map<String, Object> row = queryForMapOrNull(sql);
		if (row == null) {
			String fallbackSql =
				"SELECT season_no"
				+ "     , TO_CHAR(DATE_TRUNC('month', start_date), 'YYYYMM') AS yyyymm"
				+ "     , EXTRACT(EPOCH FROM (start_date::timestamp AT TIME ZONE 'Asia/Seoul'))::bigint AS start_epoch"
				+ "     , CASE WHEN end_date IS NULL THEN " + OPEN_END_EPOCH
				+ "            ELSE EXTRACT(EPOCH FROM ((end_date + 1)::timestamp AT TIME ZONE 'Asia/Seoul'))::bigint END AS end_epoch_excl"
				+ "  FROM guild_siege_season"
				+ " ORDER BY season_no DESC LIMIT 1";
			row = queryForMapOrNull(fallbackSql);
		}
		if (row == null) {
			return new Snapshot(null, "000000", 0L, OPEN_END_EPOCH);
		}
		Integer seasonNo = row.get("season_no") != null ? ((Number) row.get("season_no")).intValue() : null;
		String yyyymm = row.get("yyyymm") != null ? row.get("yyyymm").toString() : "000000";
		long startEpoch = row.get("start_epoch") != null ? ((Number) row.get("start_epoch")).longValue() : 0L;
		long endEpochExcl = row.get("end_epoch_excl") != null ? ((Number) row.get("end_epoch_excl")).longValue() : OPEN_END_EPOCH;
		return new Snapshot(seasonNo, yyyymm, startEpoch, endEpochExcl);
	}

	private Map<String, Object> queryForMapOrNull(String sql) {
		var list = jdbcTemplate.queryForList(sql);
		return list.isEmpty() ? null : list.get(0);
	}

	/** 현재 시즌 YYYYMM(시작월). 미변경 레거시 경로 호환용. */
	public String getCurrentSeasonYyyymm() {
		return load().yyyymm();
	}

	/** 현재 시즌 번호. 활성/폴백 시즌이 없으면 null. */
	public Integer getCurrentSeasonNo() {
		return load().seasonNo();
	}

	/** 현재 시즌 시작 00:00(KST) epoch 초. */
	public long getCurrentSeasonStartEpoch() {
		return load().startEpoch();
	}

	/** 현재 시즌 종료일+1 00:00(KST) epoch 초(상한, 미만 비교용). 진행 중이면 매우 큰 sentinel. */
	public long getCurrentSeasonEndEpochExclusive() {
		return load().endEpochExcl();
	}
}
