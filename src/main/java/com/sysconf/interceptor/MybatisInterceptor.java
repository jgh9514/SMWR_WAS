package com.sysconf.interceptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
	
	@Value("${smw.mybatis.log.result-total:false}")
	private boolean resultTotalLogEnabled;

    @SuppressWarnings("unchecked")
	public Object intercept(Invocation invocation) throws Throwable {
        // 호출 SQL 정보
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        if (log.isDebugEnabled()) {
            log.debug("MybatisInterceptor.intercept() start. mapperId={}", ms.getId());
        }
        
        // selectUserInfo는 MybatisInterceptor를 거치지 않음 (userInfo 조회 시 SessionThread에 정보가 없을 수 있음)
        if (ms.getId().contains("selectUserInfo")) {
            return invocation.proceed();
        }
        
        Map<String, Object> userInfo = SessionThread.SESSION_USER_INFO.get();
        if (log.isDebugEnabled()) {
            log.debug("MybatisInterceptor - mapperId={}, userInfoPresent={}", ms.getId(), userInfo != null);
            if (userInfo != null) {
                log.debug("MybatisInterceptor - userInfo.siege_view_scope={}", userInfo.get("siege_view_scope"));
            }
        }
        
        if (parameter instanceof Map) {
            Map<String, Object> parameters = (Map<String, Object>) parameter;
            
            // MyBatis의 MapperMethod.ParamMap은 존재하지 않는 키를 get() 하면 BindingException을 던질 수 있음
            Object preSiegeViewScope = parameters.containsKey("siege_view_scope") ? parameters.get("siege_view_scope") : null;
            if (log.isDebugEnabled()) {
                log.debug("MybatisInterceptor - siege_view_scope before={}", preSiegeViewScope);
            }
            
            if (userInfo != null) {
                // request body 우선이지만, 값이 null/빈문자열이면 세션값으로 보정
                for (Map.Entry<String, Object> entry : userInfo.entrySet()) {
                    String key = entry.getKey();
                    Object incoming = entry.getValue();
                    if (shouldInject(parameters, key)) parameters.put(key, incoming);
                }
            } else {
                // 로그인 정보가 없는 경우에도, 일부 쿼리는 siege_view_scope 파라미터를 필수로 요구함.
                // 기본값(C: 현재 시즌만)을 주입해서 BindingException을 방지한다.
                if (shouldInject(parameters, "siege_view_scope")) {
                    parameters.put("siege_view_scope", "C");
                }
            }
            if (shouldInject(parameters, "global_dblink_nm")) {
                parameters.put("global_dblink_nm", globalDblinkNm);
            }
            
            Object postSiegeViewScope = parameters.containsKey("siege_view_scope") ? parameters.get("siege_view_scope") : null;
            if (log.isDebugEnabled()) {
                log.debug("MybatisInterceptor - siege_view_scope after={}", postSiegeViewScope);
            }
        }

        long startedAt = System.currentTimeMillis();

        // 원래 실행할 SQL 가져오기
        BoundSql boundSql = ms.getBoundSql(parameter);
        String originalSQL = boundSql.getSql();
        
        if (inlineSqlLogEnabled && log.isDebugEnabled()) {
        	try {
        		String inlined = buildInlinedSql(ms, boundSql, parameter);
        		log.debug("SQL(inlined) mapperId={}\n{}", ms.getId(), inlined);
        	} catch (Exception e) {
        		// 인라인 SQL 로깅은 디버깅용이므로 실패해도 흐름에 영향 주지 않음
        		log.debug("SQL(inlined) build failed. mapperId={}", ms.getId(), e);
        	}
        }

        // 인터셉터 거쳤는지 확인
        if (originalSQL.contains("COMMON_PAGE_SEARCH")) {
            Object r = invocation.proceed();
            logResultTotalIfNeeded(invocation, ms, startedAt, r);
            return r;
        }

        if (parameter instanceof Map) {
            Map<String, Object> parameterMap = (Map<String, Object>) parameter;
            if (parameterMap == null || !parameterMap.containsKey("COMMON_SEARCH_PAGE_INFO")) {
                return invocation.proceed();
            } else if ("N".equals(parameterMap.get("COMMON_AUTO_CONDITION"))) {
                return invocation.proceed();
            }

            parameterMap.replaceAll((key, value) -> value != null ? String.valueOf(value).replaceAll("[';\"\\\\/#/*]", "") : null);

            List<Map<String, Object>> pageSearchList = PageSearchResult.PAGE_ITEM_LIST.get(parameterMap.get("COMMON_SEARCH_PAGE_INFO"));

            StringBuilder wrapperSQL = new StringBuilder();
            wrapperSQL
                    .append("SELECT * ")
                    .append("  FROM ( ")
                    .append(originalSQL)
                    .append("  ) COMMON_PAGE_SEARCH ")
                    .append(" WHERE 1 = 1 ");

            String[] EQUAL_ELEMENT = {"WKPL", "DEPT", "YEAR", "USER", "SELECT", "CMPY", "LOC", "CALENDAR"};
            List<String> EQUAL_ELEMENT_LIST = new ArrayList<>(Arrays.asList(EQUAL_ELEMENT));

            String[] PRCS_ELEMENT = {"PRCS"};
            List<String> PRCS_ELEMENT_LIST = new ArrayList<>(Arrays.asList(PRCS_ELEMENT));

            // like 조건?�로 ?�어�?Element
            String[] LIKE_ELEMENT = {"TEXT"};
            List<String> LIKE_ELEMENT_LIST = new ArrayList<>(Arrays.asList(LIKE_ELEMENT));

            // calendar 조건?�로 ?�어�?Element
//            String[] BETWEEN_ELEMENT = {"calendar"};
//            List<String> BETWEEN_ELEMENT_LIST = new ArrayList<>(Arrays.asList(BETWEEN_ELEMENT));


            if (pageSearchList != null) {
                for (Map<String, Object> pageSearchParam : pageSearchList) {
                    String ELEMENT_TYPE = (String) pageSearchParam.get("element_cd");

                    String BIND_COLUMN_NM = (String) pageSearchParam.get("bind_column_nm");
                    String BIND_COLUMN_VALUE = null;
                    if (parameterMap.get(BIND_COLUMN_NM) != null) {
                        BIND_COLUMN_VALUE = parameterMap.get(BIND_COLUMN_NM).toString();
                    }

    //                String BIND_CALENDAR_S_COLUMN_NM = (String) pageSearchParam.get("calendar_from_model_id");
    //                String BIND_CALENDAR_S_COLUMN_VALUE = parameterMap.get(BIND_CALENDAR_S_COLUMN_NM);
    //
    //                String BIND_CALENDAR_E_COLUMN_NM = (String) pageSearchParam.get("calendar_to_model_id");
    //                String BIND_CALENDAR_E_COLUMN_VALUE = parameterMap.get(BIND_CALENDAR_E_COLUMN_NM);

                    if (EQUAL_ELEMENT_LIST.contains(ELEMENT_TYPE) && BIND_COLUMN_VALUE != null && !"".equals(BIND_COLUMN_VALUE)) {
                        // '=' 조건?�로 가?�한 ?�리먼트 ?�??
                        wrapperSQL.append(" AND COMMON_PAGE_SEARCH." + BIND_COLUMN_NM).append(" = UPPER('").append(BIND_COLUMN_VALUE).append("')");
                    } else if (LIKE_ELEMENT_LIST.contains(ELEMENT_TYPE) && BIND_COLUMN_VALUE != null && !"".equals(BIND_COLUMN_VALUE)) {
                        // LIKE 조건?�로 가?�한 ?�리먼트 ?�??
                        wrapperSQL.append(" AND UPPER(COMMON_PAGE_SEARCH." + BIND_COLUMN_NM).append(") LIKE UPPER('%").append(BIND_COLUMN_VALUE).append("%')");
                    } else if (PRCS_ELEMENT_LIST.contains(ELEMENT_TYPE)) {
                        int prcsLevelVal = ((BigDecimal) pageSearchParam.get("prcs_level_val")).intValue();

                        for (int i = 1; i < prcsLevelVal + 1; i++) {
                            BIND_COLUMN_VALUE = null;

                            if (i == 1) {
                                BIND_COLUMN_NM = "prcs_dept_id";
                            } else if (i == 2) {
                                BIND_COLUMN_NM = "prcs_id";
                            } else if (i == 3) {
                                BIND_COLUMN_NM = "prcs_dtl_id";
                            } else if (i == 4) {
                                BIND_COLUMN_NM = "prcs_dtl_atvt_id";
                            }

                            if (parameterMap.get(BIND_COLUMN_NM) != null) {
                                BIND_COLUMN_VALUE = parameterMap.get(BIND_COLUMN_NM).toString();
                            }
                            if (BIND_COLUMN_VALUE != null && !"".equals(BIND_COLUMN_VALUE)) {
                                wrapperSQL.append(" AND COMMON_PAGE_SEARCH." + BIND_COLUMN_NM).append(" = '").append(BIND_COLUMN_VALUE).append("'");
                            }
                        }
                    }
                }
            }

            if ("Y".equals(parameterMap.get("COMMON_ROLE_WKPL_CONDITION"))) {
                List<String> wkplRoleList = null;
                if (userInfo != null) {
                    if (userInfo.get("sess_wkpl_role") != null) {
                        wkplRoleList = (List<String>) userInfo.get("sess_wkpl_role");
                    } else if (userInfo.get("sess_wkpl_id") != null) {
                        wkplRoleList = Arrays.asList(userInfo.get("sess_wkpl_id").toString());
                    }
                }

                if (wkplRoleList != null && !wkplRoleList.isEmpty()) {
                    wrapperSQL.append(" AND COMMON_PAGE_SEARCH.WKPL_ID IN (");
                    wrapperSQL.append("'" + String.join("','", wkplRoleList) + "'");
                    wrapperSQL.append(")");
                }
            }

            String searchSQL = wrapperSQL.toString();

            // ??쿼리 주입
            BoundSql newBoundSql = new BoundSql(
                    ms.getConfiguration(),
                    searchSQL,
                    boundSql.getParameterMappings(),
                    boundSql.getParameterObject()
            );
            MappedStatement newMs = copyFromMappedStatement(ms, new BoundSqlSqlSource(newBoundSql));
            invocation.getArgs()[0] = newMs;
        }

        Object result = invocation.proceed();
        logResultTotalIfNeeded(invocation, ms, startedAt, result);
        if (log.isDebugEnabled()) {
            log.debug("MybatisInterceptor.intercept() end. mapperId={}", ms.getId());
        }
        return result;
    }

    private boolean shouldInject(Map<String, Object> parameters, String key) {
        if (!parameters.containsKey(key)) return true;
        Object current = parameters.get(key);
        if (current == null) return true;
        if (current instanceof String && ((String) current).trim().isEmpty()) return true;
        return false;
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
    	if (value instanceof java.util.Date) {
    		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    		return "'" + fmt.format((java.util.Date) value) + "'";
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
    
    @SuppressWarnings({ "rawtypes" })
    private void logResultTotalIfNeeded(Invocation invocation, MappedStatement ms, long startedAt, Object result) {
    	if (!resultTotalLogEnabled) return;
    	if (ms == null) return;
    	
    	String method = invocation != null && invocation.getMethod() != null ? invocation.getMethod().getName() : "";
    	long elapsedMs = Math.max(0, System.currentTimeMillis() - startedAt);
    	
    	try {
    		if ("query".equals(method)) {
    			int total = -1;
    			if (result instanceof List) total = ((List) result).size();
    			// 조회 결과(total) 로그는 기본적으로 출력하지 않음 (필요 시 DEBUG + 설정으로만 확인)
    			if (log.isDebugEnabled()) {
    				log.debug("MyBatis Total mapperId={}, total={}, elapsedMs={}", ms.getId(), total, elapsedMs);
    			}
    		} else if ("update".equals(method)) {
    			// update 요약도 DEBUG로만 출력 (노이즈 억제)
    			if (log.isDebugEnabled()) {
    				log.debug("MyBatis Update mapperId={}, affected={}, elapsedMs={}", ms.getId(), result, elapsedMs);
    			}
    		}
    	} catch (Exception ignore) {
    		// no-op
    	}
    }

    private static class BoundSqlSqlSource implements SqlSource {
        private final BoundSql boundSql;

        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }

    private MappedStatement copyFromMappedStatement(MappedStatement ms, BoundSqlSqlSource newSqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                ms.getConfiguration(),
                ms.getId(),
                newSqlSource,
                ms.getSqlCommandType()
        );

        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.cache(ms.getCache());

        return builder.build();
    }
}


