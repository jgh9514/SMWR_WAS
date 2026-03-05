package com.sysconf.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class DateUtil {

    @Value("${spring.datasource.url}")
    public String datasourceUrl;

    public String now() {
        return new SimpleDateFormat(getPattern()).format(new Date());
    }

    public String getPattern() {
        if (datasourceUrl.indexOf("sqlserver") > 0) {
            return "yyyy-MM-dd HH:mm:ss";
        } else if (datasourceUrl.indexOf("postgresql") > 0) {
            return "yyyyMMddHHmmss";
        } else if (datasourceUrl.indexOf("oracle") > 0) {
            return "yyyyMMddHHmmss";
        }
        return "yyyy-MM-dd HH:mm:ss";
    }
}

