package com.sysconf.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sysconf.cache.CurrentSeasonCache;
import com.sysconf.logging.LogPayloadTrimmer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Intercepts(
        {
                @Signature(
                    type = Executor.class,
                    method = "query",
                    args = {
                            MappedStatement.class,
                            Object.class,
                            RowBounds.class,
                            ResultHandler.class
                    }
                ),
                @Signature(
                    type = Executor.class,
                    method = "update",
                    args = {
                            MappedStatement.class,
                            Object.class,
                    }
                )
        }
)
public class MybatisInterceptor implements Interceptor {

	@Value("${smw.globalDblinkNm}")
	private String globalDblinkNm;
	
	@Value("${smw.mybatis.log.inline-sql:false}")
	private boolean inlineSqlLogEnabled;
	
	/** @deprecated 조회 total·update affected 는 로그에 남기지 않음(정책). 설정 무시. */
	@Deprecated
	@Value("${smw.mybatis.log.result-total:false}")
	@SuppressWarnings("unused")
	private boolean resultTotalLogEnabled;

	/** true: 관리 배치 API(/api/v1/batch) 처리 중 MybatisInterceptor DEBUG·SQL(inlined) 생략 */
	@Value("${smw.mybatis.interceptor.silent-trace-on-batch-api:true}")
	private boolean silentTraceOnBatchApi;

	@Autowired(required = false)
	private CurrentSeasonCache currentSeasonCache;

    @SuppressWarnings("unchecked")
	public Object intercept(Invocation invocation) throws Throwable {
        // 호출 SQL 정보
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        final String mapperId = ms.getId();
        /** RTA 등 점령(siege) 스코프·세션 주입과 무관한 매퍼 — trace/siege_view_scope DEBUG 생략 */
        final boolean skipSiegeInjection = mapperId.contains("RtaMapper");
        final boolean isLogMapper = mapperId.contains("LogMapper");

        if (emitInterceptorTraceDebug() && !skipSiegeInjection) {
            log.debug("MybatisInterceptor.intercept() start. mapperId={}", mapperId);
        }

        // selectUserInfo / selectUserRoles / selectDvcId: 로그인·역할·생체 조회 — 세션·siege 주입 없이 실행.
        if (mapperId.contains("selectUserInfo") || mapperId.contains("selectUserRoles") || mapperId.contains("selectDvcId")
                || mapperId.contains("selectRtaSeasonsForRtaMatchMapping")) {
            logInlinedSql(ms, parameter);
            return invocation.proceed();
        }
        // LogMapper: siege 주입·시즌 캐시 제외, 숫자 파라미터만 보정
        if (isLogMapper) {
            if (parameter instanceof Map) {
                Map<String, Object> params = (Map<String, Object>) parameter;
                sanitizeNumericParams(params);
            }
            logInlinedSql(ms, parameter);
            return invocation.proceed();
        }

        Map<String, Object> userInfo = SessionThread.SESSION_USER_INFO.get();
        if (emitInterceptorTraceDebug() && !skipSiegeInjection) {
            log.debug("MybatisInterceptor - mapperId={}, userInfoPresent={}", mapperId, userInfo != null);
            if (userInfo != null) {
                log.debug("MybatisInterceptor - userInfo.siege_view_scope={}", userInfo.get("siege_view_scope"));
            }
        }
        
        if (parameter instanceof Map) {
            Map<String, Object> parameters = (Map<String, Object>) parameter;
            
            // MyBatis의 MapperMethod.ParamMap은 존재하지 않는 키를 get() 하면 BindingException을 던질 수 있음
            Object preSiegeViewScope = parameters.containsKey("siege_view_scope") ? parameters.get("siege_view_scope") : null;
            if (emitInterceptorTraceDebug() && !skipSiegeInjection) {
                log.debug("MybatisInterceptor - siege_view_scope before={}", preSiegeViewScope);
            }
            
            if (!skipSiegeInjection && userInfo != null) {
                // request body 우선이지만, 값이 null/빈문자열이면 세션값으로 보정
                for (Map.Entry<String, Object> entry : userInfo.entrySet()) {
                    String key = entry.getKey();
                    Object incoming = entry.getValue();
                    if (shouldInject(parameters, key)) parameters.put(key, incoming);
                }
            } else if (!skipSiegeInjection) {
                // 로그인 정보가 없는 경우에도, 일부 쿼리는 siege_view_scope 파라미터를 필수로 요구함.
                // 기본값(C: 현재 시즌만)을 주입해서 BindingException을 방지한다.
                if (shouldInject(parameters, "siege_view_scope")) {
                    parameters.put("siege_view_scope", "C");
                }
            }
            if (!skipSiegeInjection && shouldInject(parameters, "global_dblink_nm")) {
                parameters.put("global_dblink_nm", globalDblinkNm);
            }
            // siege_defense_deck_stats·guild_agg 조회 시 guild_siege_season 서브쿼리 제거 (CurrentSeasonCache → current_season_yyyymm)
            if (!skipSiegeInjection && currentSeasonCache != null && ms.getId().contains("summonerswarMapper")
                    && shouldInject(parameters, "current_season_yyyymm")) {
                Object scope = parameters.get("siege_view_scope");
                if (scope == null || !"A".equals(scope.toString())) {
                    parameters.put("current_season_yyyymm", currentSeasonCache.getCurrentSeasonYyyymm());
                }
            }
            // NumberFormatException 방지: 숫자 파라미터·몬스터ID 컬렉션 검증
            if (!skipSiegeInjection) {
                sanitizeNumericParams(parameters);
                sanitizeMonsterIdCollections(parameters);
                // ParamMap 등 래핑된 Map 내부도 검증
                for (Object v : parameters.values()) {
                    if (v instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nested = (Map<String, Object>) v;
                        sanitizeNumericParams(nested);
                        sanitizeMonsterIdCollections(nested);
                    }
                }
            }
            
            Object postSiegeViewScope = parameters.containsKey("siege_view_scope") ? parameters.get("siege_view_scope") : null;
            if (emitInterceptorTraceDebug() && !skipSiegeInjection) {
                log.debug("MybatisInterceptor - siege_view_scope after={}", postSiegeViewScope);
            }
        }

        logInlinedSql(ms, parameter);

        Object result = invocation.proceed();
        if (emitInterceptorTraceDebug() && !skipSiegeInjection) {
            log.debug("MybatisInterceptor.intercept() end. mapperId={}", mapperId);
        }
        return result;
    }

	/** 배치 관리 API 요청 스레드에서는 DEBUG·SQL(inlined) 노이즈 억제(로거 레벨은 그대로). */
	private boolean suppressBatchManagementApiNoise() {
		return silentTraceOnBatchApi && isBatchManagementApiRequest();
	}

	private boolean emitInterceptorTraceDebug() {
		if (!log.isDebugEnabled()) {
			return false;
		}
		return !suppressBatchManagementApiNoise();
	}

	private static boolean isBatchManagementApiRequest() {
		try {
			var attrs = RequestContextHolder.getRequestAttributes();
			if (!(attrs instanceof ServletRequestAttributes sra)) {
				return false;
			}
			String uri = sra.getRequest().getRequestURI();
			return uri != null && uri.contains("/api/v1/batch");
		} catch (Exception e) {
			return false;
		}
	}

    /**
     * smw.mybatis.log.inline-sql=true 이면 ? 를 리터럴로 치환한 SQL 을 INFO 로 출력 (별도 로거 DEBUG 불필요).
     */
    private void logInlinedSql(MappedStatement ms, Object parameter) {
        if (!inlineSqlLogEnabled || suppressBatchManagementApiNoise()) {
            return;
        }
        try {
            BoundSql boundSql = ms.getBoundSql(parameter);
            String inlined = buildInlinedSql(ms, boundSql, parameter);
            String safe = LogPayloadTrimmer.truncateUtf8(inlined, LogPayloadTrimmer.DEFAULT_MAX_MESSAGE_BYTES);
            log.debug("SQL(inlined) mapperId={}\n{}", ms.getId(), safe);
        } catch (Exception e) {
            log.warn("SQL(inlined) build failed. mapperId={}", ms.getId(), e);
        }
    }

    private boolean shouldInject(Map<String, Object> parameters, String key) {
        if (!parameters.containsKey(key)) return true;
        Object current = parameters.get(key);
        if (current == null) return true;
        if (current instanceof String && ((String) current).trim().isEmpty()) return true;
        return false;
    }

    /** 숫자 파라미터에 "A" 등 잘못된 값이 들어가 NumberFormatException 발생 방지 */
    private void sanitizeNumericParams(Map<String, Object> parameters) {
        String[] numericKeys = {"paging", "offset", "limit", "page", "historyLimit", "historyOffset", "recommendedLimit", "recommendedOffset", "recommendedPaging", "min_lose_count", "http_status", "elapsed_ms"};
        int[] defaults = {10, 1, 20, 1, 10, 1, 5, 1, 5, 0, 200, 0};
        for (int i = 0; i < numericKeys.length; i++) {
            String key = numericKeys[i];
            if (!parameters.containsKey(key)) continue;
            Object v = parameters.get(key);
            if (v == null) {
                parameters.put(key, defaults[i]);
                continue;
            }
            if (v instanceof Number) {
                int n = ((Number) v).intValue();
                if (n < 0) parameters.put(key, defaults[i]);
                continue;
            }
            try {
                int n = Integer.parseInt(v.toString().trim());
                parameters.put(key, n < 0 ? defaults[i] : n);
            } catch (NumberFormatException e) {
                parameters.put(key, defaults[i]);
            }
        }
    }
    
    /** monster_id*_ids 컬렉션에서 비숫자 값 제거 (NumberFormatException 방지) */
    private void sanitizeMonsterIdCollections(Map<String, Object> parameters) {
        String[] keys = {"monster_id1_ids", "monster_id2_ids", "monster_id3_ids"};
        for (String key : keys) {
            if (!parameters.containsKey(key)) continue;
            Object val = parameters.get(key);
            if (!(val instanceof List)) continue;
            List<?> list = (List<?>) val;
            List<String> filtered = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                String s = item.toString().trim();
                if (s.isEmpty()) continue;
                try {
                    Long.parseLong(s);
                    filtered.add(s);
                } catch (NumberFormatException ignored) {
                    // 비숫자 ID 제외
                }
            }
            parameters.put(key, filtered);
        }
    }
    
    private String buildInlinedSql(MappedStatement ms, BoundSql boundSql, Object parameterObject) {
    	String sql = boundSql.getSql();
    	if (sql == null) return "";
    	sql = sql.replaceAll("\\s+", " ").trim();
    	
    	List<ParameterMapping> mappings = boundSql.getParameterMappings();
    	if (mappings == null || mappings.isEmpty()) return sql;

    	MetaObject metaObject = (parameterObject == null) ? null : ms.getConfiguration().newMetaObject(parameterObject);
    	StringBuilder out = new StringBuilder(sql.length() + 64);
    	
    	int mappingIdx = 0;
    	for (int i = 0; i < sql.length(); i++) {
    		char c = sql.charAt(i);
    		if (c == '?' && mappingIdx < mappings.size()) {
    			ParameterMapping pm = mappings.get(mappingIdx++);
    			Object value = resolveParamValue(boundSql, metaObject, parameterObject, pm.getProperty());
    			out.append(formatSqlLiteral(value, pm.getProperty()));
    		} else {
    			out.append(c);
    		}
    	}
    	return out.toString();
    }
    
    private Object resolveParamValue(BoundSql boundSql, MetaObject metaObject, Object parameterObject, String property) {
    	if (property == null) return null;
    	// foreach 등에서 추가 파라미터가 생기는 경우
    	if (boundSql.hasAdditionalParameter(property)) {
    		return boundSql.getAdditionalParameter(property);
    	}
    	// mybatis 내부가 __frch_XXX 형태를 추가 파라미터로 넣는 경우도 있음
    	if (property.startsWith("__frch_") && boundSql.hasAdditionalParameter(property)) {
    		return boundSql.getAdditionalParameter(property);
    	}
    	if (parameterObject == null) return null;
    	if (parameterObject instanceof Map) {
    		return ((Map<?, ?>) parameterObject).get(property);
    	}
    	if (metaObject == null) {
    		metaObject = SystemMetaObject.forObject(parameterObject);
    	}
    	if (metaObject.hasGetter(property)) {
    		return metaObject.getValue(property);
    	}
    	// 단일 파라미터(primitive/string)일 수 있음
    	return parameterObject;
    }
    
    private String formatSqlLiteral(Object value, String property) {
    	if (value == null) return "NULL";
    	
    	// 민감정보 마스킹
    	if (property != null) {
    		String p = property.toLowerCase();
    		if (p.contains("password") || p.contains("passwd") || p.contains("pwd")) {
    			return "'******'";
    		}
    	}
    	
    	if (value instanceof Number || value instanceof Boolean) {
    		return String.valueOf(value);
    	}
    	if (value instanceof long[] arr) {
    		return formatLongArrayForSqlLog(arr);
    	}
    	if (value instanceof Collection<?> coll) {
    		return LogPayloadTrimmer.formatCollectionForSqlLog(coll);
    	}
    	if (value instanceof java.util.Date) {
    		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    		return "'" + fmt.format((java.util.Date) value) + "'";
    	}
    	if (value instanceof java.time.LocalDateTime) {
    		return "'" + value.toString().replace('T', ' ') + "'";
    	}
    	if (value instanceof java.time.LocalDate) {
    		return "'" + value.toString() + "'";
    	}
    	if (value instanceof java.time.Instant) {
    		return "'" + value.toString() + "'";
    	}
    	
    	String s = String.valueOf(value);
    	// 너무 긴 값은 잘라서 로그 폭발 방지
    	if (s.length() > 200) {
    		s = s.substring(0, 200) + "...";
    	}
    	// SQL string literal escape
    	s = s.replace("'", "''");
    	return "'" + s + "'";
    }

    /** 인라인 SQL 로그용 — 실제 바인딩은 TypeHandler 사용. 대량이면 앞부분만 + 개수 */
    private static String formatLongArrayForSqlLog(long[] arr) {
    	if (arr == null) {
    		return "NULL";
    	}
    	int n = arr.length;
    	int show = Math.min(n, 32);
    	StringBuilder sb = new StringBuilder(show * 12 + 48);
    	sb.append("ARRAY[");
    	for (int i = 0; i < show; i++) {
    		if (i > 0) {
    			sb.append(',');
    		}
    		sb.append(arr[i]);
    	}
    	if (n > show) {
    		sb.append(",… /* total=").append(n).append(" */");
    	}
    	sb.append("]::bigint[]");
    	return sb.toString();
    }
    
}


