package com.smw.infra.cache;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sysconf.interceptor.SessionThread;

@Configuration
public class CacheKeyConfig {

	@Bean("stableMapKeyGenerator")
	public KeyGenerator stableMapKeyGenerator(ObjectMapper objectMapper) {
		final ObjectMapper mapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

		return new KeyGenerator() {
			@Override
			public Object generate(Object target, Method method, Object... params) {
				if (params == null || params.length == 0) {
					return method.getName();
				}
				try {
					// Map/List 등도 키 순서가 안정적으로 직렬화되도록 설정
					return method.getName() + ":" + mapper.writeValueAsString(params);
				} catch (JsonProcessingException e) {
					// 직렬화 실패 시에도 캐시 키가 생성되도록 fallback
					return method.getName() + ":" + java.util.Arrays.deepToString(params);
				}
			}
		};
	}

	/**
	 * 몬스터 상세 캐시 키 — 요청 body의 siege_view_scope·길드 컨텍스트를 포함한다.
	 * body에 scope가 없을 때만 세션(JWT) scope를 ctx에 넣는다.
	 */
	@Bean("monsterDetailKeyGenerator")
	public KeyGenerator monsterDetailKeyGenerator(ObjectMapper objectMapper) {
		final ObjectMapper mapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

		return new KeyGenerator() {
			@Override
			public Object generate(Object target, Method method, Object... params) {
				try {
					StringBuilder key = new StringBuilder(method.getName()).append(":");
					key.append(mapper.writeValueAsString(params));
					Map<String, Object> session = SessionThread.SESSION_USER_INFO.get();
					if (session != null) {
						Map<String, Object> ctx = new LinkedHashMap<>();
						if (!hasSiegeViewScopeInParams(params)) {
							appendSessionKey(ctx, session, "siege_view_scope");
						}
						appendSessionKey(ctx, session, "sess_guild_id");
						appendSessionKey(ctx, session, "view_guild_id");
						appendSessionKey(ctx, session, "view_all_guilds");
						key.append(":ctx=").append(mapper.writeValueAsString(ctx));
					} else {
						key.append(":ctx=none");
					}
					return key.toString();
				} catch (JsonProcessingException e) {
					return method.getName() + ":" + java.util.Arrays.deepToString(params);
				}
			}

			private boolean hasSiegeViewScopeInParams(Object... params) {
				if (params == null) {
					return false;
				}
				for (Object p : params) {
					if (p instanceof Map<?, ?> map && map.containsKey("siege_view_scope")) {
						Object scope = map.get("siege_view_scope");
						return scope != null && !String.valueOf(scope).isBlank();
					}
				}
				return false;
			}

			private void appendSessionKey(Map<String, Object> ctx, Map<String, Object> session, String key) {
				if (!session.containsKey(key)) {
					return;
				}
				Object value = session.get(key);
				if (value != null) {
					ctx.put(key, value);
				}
			}
		};
	}
}

