package com.sysconf.interceptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    @SuppressWarnings("unchecked")
	public Object intercept(Invocation invocation) throws Throwable {
        // ?�출??SQL?�보
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        Map<String, Object> userInfo = SessionThread.SESSION_USER_INFO.get();
        if (parameter instanceof Map) {
            Map<String, Object> parameters = (Map<String, Object>) parameter;
            if (userInfo != null) {
                parameters.putAll(userInfo);
            }
            parameters.put("global_dblink_nm", globalDblinkNm);
        }

        // ?�래 ?�행??SQL 가?�오�?
        BoundSql boundSql = ms.getBoundSql(parameter);
        String originalSQL = boundSql.getSql();

        // ?�터?�터 거쳤?��? ?�인
        if (originalSQL.contains("COMMON_PAGE_SEARCH")) {
            return invocation.proceed();
        }

        if (parameter instanceof Map) {
            Map<String, Object> parameterMap = (Map<String, Object>) parameter;
            // ?�이지번호가 ?�으�?
            if (parameterMap == null || !parameterMap.containsKey("COMMON_SEARCH_PAGE_INFO")) {
                return invocation.proceed();
            } else if ("N".equals(parameterMap.get("COMMON_AUTO_CONDITION"))) {
                return invocation.proceed();
            }

            // ?�라미터???�수문자(' ; " \ / # *) ?�외
            parameterMap.replaceAll((key, value) -> value != null ? String.valueOf(value).replaceAll("[';\"\\\\/#/*]", "") : null);

            // ?�재 ?�이지 조회조건 가?��???조건 쿼리 ?�성
            List<Map<String, Object>> pageSearchList = PageSearchResult.PAGE_ITEM_LIST.get(parameterMap.get("COMMON_SEARCH_PAGE_INFO"));

            StringBuilder wrapperSQL = new StringBuilder();
            wrapperSQL
                    .append("SELECT * ")
                    .append("  FROM ( ")
                    .append(originalSQL)
                    .append("  ) COMMON_PAGE_SEARCH ")
                    .append(" WHERE 1 = 1 ");

            // = 조건?�로 ?�어�?Element
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
                if (userInfo.get("sess_wkpl_role") != null) {
                    wkplRoleList = (List<String>) userInfo.get("sess_wkpl_role");
                } else {
                    wkplRoleList = Arrays.asList(userInfo.get("sess_wkpl_id").toString());
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

        return invocation.proceed();
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


