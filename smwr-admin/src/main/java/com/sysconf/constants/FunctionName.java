package com.sysconf.constants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class FunctionName {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public static String FN_GET_CD = "fn_get_cd";
    public static String FN_GET_NOW = "fn_get_now";
    public static String FN_GET_TIME = "fn_get_time";
    public static String FN_GET_USER = "fn_get_user";
    public static String FN_GET_MLANG_CD = "fn_get_mlang_cd";
    
    public static String FN_LPAD = "fn_lpad";
    public static String FN_NVL = "fn_nvl";

    public FunctionName() {}

    @PostConstruct
    public void postConstructor() {
        System.out.println("datasource url : " + datasourceUrl);
        if (datasourceUrl.indexOf("sqlserver") > 0) {
        } else if (datasourceUrl.indexOf("postgresql") > 0) {
        } else {
        }
    }
}

