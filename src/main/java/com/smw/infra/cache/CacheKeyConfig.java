package com.smw.infra.cache;

import java.lang.reflect.Method;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

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
}

