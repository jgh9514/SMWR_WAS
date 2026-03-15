package com.sysconf.util;

import java.util.Map;

public class StringUtil {
	public static String nvl(String str) {
		if (null==str || "".equals(str)) {
			str = "";
		}
		return str;
	}

	/**
	 * Map에서 안전하게 String 값을 가져오는 메서드
	 * @param map 대상 Map
	 * @param key 가져올 key
	 * @return key에 해당하는 값의 toString() 결과, 없으면 null
	 */
	public static String getParam(Map<String, Object> map, String key) {
		if (map == null || key == null) {
			return null;
		}
		Object value = map.get(key);
		return value != null ? value.toString() : null;
	}
}

