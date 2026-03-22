package com.sysconf.cache;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 현재 점령전 시즌(YYYYMM) 캐시.
 * guild_siege_season 서브쿼리 반복 실행을 피하고 조회 성능을 높이기 위해 사용.
 */
@Slf4j
@Component
public class CurrentSeasonCache {

	private static final long TTL_MS = 5 * 60 * 1000; // 5분

	private final JdbcTemplate jdbcTemplate;
	private volatile String cached;
	private final AtomicLong cachedAt = new AtomicLong(0);

	public CurrentSeasonCache(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 현재 시즌 YYYYMM 반환. 캐시 만료 시 DB 조회 후 갱신.
	 */
	public String getCurrentSeasonYyyymm() {
		long now = System.currentTimeMillis();
		if (cached != null && (now - cachedAt.get()) < TTL_MS) {
			return cached;
		}
		synchronized (this) {
			if (cached != null && (now - cachedAt.get()) < TTL_MS) {
				return cached;
			}
			try {
				String yyyymm = jdbcTemplate.queryForObject(
					"SELECT COALESCE("
					+ "(SELECT TO_CHAR(DATE_TRUNC('month', start_date), 'YYYYMM') FROM guild_siege_season"
					+ " WHERE start_date <= CURRENT_DATE AND (end_date IS NULL OR end_date >= CURRENT_DATE)"
					+ " ORDER BY season_no DESC LIMIT 1),"
					+ " (SELECT TO_CHAR(DATE_TRUNC('month', start_date), 'YYYYMM') FROM guild_siege_season ORDER BY season_no DESC LIMIT 1),"
					+ " '000000')",
					String.class);
				cached = yyyymm != null ? yyyymm : "000000";
				cachedAt.set(System.currentTimeMillis());
				return cached;
			} catch (Exception e) {
				log.warn("현재 시즌 조회 실패, 기본값 사용", e);
				cached = "000000";
				cachedAt.set(now);
				return cached;
			}
		}
	}
}
