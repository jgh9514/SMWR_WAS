package com.sysconf.interceptor;

import java.util.Map;

public class SessionThread {
    public static ThreadLocal<Map<String, Object>> SESSION_USER_INFO = new ThreadLocal<Map<String, Object>>();
}
