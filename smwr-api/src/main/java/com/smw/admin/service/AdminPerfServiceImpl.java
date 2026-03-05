package com.smw.admin.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.admin.mapper.AdminPerfMapper;

@Service
@Primary
public class AdminPerfServiceImpl implements AdminPerfService {

	@Autowired
	private AdminPerfMapper adminPerfMapper;

	private boolean hasColumn(Set<String> cols, String name) {
		return cols.contains(name);
	}

	@Override
	public Map<String, Object> getDiagnostics(Map<String, Object> param) {
		Map<String, Object> p = (param != null) ? param : new HashMap<>();
		return adminPerfMapper.selectPgStatStatementsDiagnostics(p);
	}

	@Override
	public List<Map<String, Object>> getSlowQueries(Map<String, Object> param) {
		Map<String, Object> p = (param != null) ? param : new HashMap<>();
		Map<String, Object> diag = adminPerfMapper.selectPgStatStatementsDiagnostics(new HashMap<>());
		// pg_stat_statements는 shared_preload_libraries로 preload 되어야 사용 가능
		Object preloadObj = diag != null ? diag.get("shared_preload_libraries") : null;
		String preload = preloadObj != null ? String.valueOf(preloadObj) : "";
		if (preload == null) preload = "";
		boolean preloaded = preload.toLowerCase().contains("pg_stat_statements");
		if (!preloaded) {
			throw new IllegalStateException(
				"pg_stat_statements must be loaded via \"shared_preload_libraries\". " +
				"DB 설정(shared_preload_libraries)에 pg_stat_statements를 추가하고 PostgreSQL을 재시작하세요."
			);
		}

		Object extInstalledObj = diag != null ? diag.get("ext_installed") : null;
		boolean extInstalled = false;
		if (extInstalledObj instanceof Boolean) extInstalled = (Boolean) extInstalledObj;
		else if (extInstalledObj != null) extInstalled = "true".equalsIgnoreCase(String.valueOf(extInstalledObj));
		if (!extInstalled) {
			throw new IllegalStateException(
				"pg_stat_statements extension is not installed. " +
				"DB에서 CREATE EXTENSION IF NOT EXISTS pg_stat_statements; 를 실행하세요."
			);
		}

		Object viewReg = diag != null ? diag.get("view_regclass") : null;
		Object viewPub = diag != null ? diag.get("view_public") : null;
		Object viewCat = diag != null ? diag.get("view_pg_catalog") : null;
		boolean hasView =
			(viewReg != null && String.valueOf(viewReg).trim().length() > 0) ||
			(viewPub != null && String.valueOf(viewPub).trim().length() > 0) ||
			(viewCat != null && String.valueOf(viewCat).trim().length() > 0);
		if (!hasView) {
			throw new IllegalStateException("pg_stat_statements 뷰를 찾을 수 없습니다. (CREATE EXTENSION / shared_preload_libraries 설정 필요)");
		}

		Set<String> cols = new HashSet<>();
		try {
			List<Map<String, Object>> c = adminPerfMapper.selectPgStatStatementsColumns(new HashMap<>());
			for (Map<String, Object> row : c) {
				if (row == null) continue;
				Object v = row.get("column_name");
				if (v != null) cols.add(String.valueOf(v));
			}
		} catch (Exception e) {
			// pg_stat_statements가 없거나 권한이 없으면 상위에서 처리
			throw e;
		}

		// PostgreSQL 13+: total_exec_time/mean_exec_time/max_exec_time
		if (hasColumn(cols, "total_exec_time") || hasColumn(cols, "mean_exec_time")) {
			// 일부 환경/확장 버전에서는 IO timing 컬럼이 없을 수 있어 동적 처리
			p.put("has_blk_read_time", hasColumn(cols, "blk_read_time"));
			p.put("has_blk_write_time", hasColumn(cols, "blk_write_time"));
			return adminPerfMapper.selectSlowQueriesV13(p);
		}
		p.put("has_blk_read_time", hasColumn(cols, "blk_read_time"));
		p.put("has_blk_write_time", hasColumn(cols, "blk_write_time"));
		return adminPerfMapper.selectSlowQueriesLegacy(p);
	}

	@Override
	public List<Map<String, Object>> getRunningQueries(Map<String, Object> param) {
		Map<String, Object> p = (param != null) ? param : new HashMap<>();
		return adminPerfMapper.selectRunningQueries(p);
	}

	@Override
	public void resetQueryStats(Map<String, Object> param) {
		Map<String, Object> p = (param != null) ? param : new HashMap<>();
		adminPerfMapper.resetPgStatStatements(p);
	}
}

