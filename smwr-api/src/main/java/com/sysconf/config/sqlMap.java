package com.sysconf.config;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang.StringUtils;

public class sqlMap extends ListOrderedMap<String, Object> {
    private static final long serialVersionUID = 1L;
    
    @Override
    public Object put(String key, Object value) {
        // StringUtils.lowerCase 로 key값을 소문자로 변경 (USER_NAME => user_name)
        // JdbcUtils.convertUnderscoreNameToPropertyName 로 key값을 camelCase로 변경 (user_name => userName)
        return super.put(StringUtils.lowerCase(key), value);
    }
}

