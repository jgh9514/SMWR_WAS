package com.sysconf.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sysconf.interceptor.AdminAuthInterceptor;
import com.sysconf.interceptor.ApiLoggingInterceptor;
import com.sysconf.interceptor.AuthSessionInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${smw.http-client.connect-timeout-ms:10000}")
	private long httpConnectTimeoutMs;

	@Value("${smw.http-client.read-timeout-ms:10000}")
	private long httpReadTimeoutMs;
	
	@Autowired
	AuthSessionInterceptor authSessionInterceptor;

	@Autowired
	AdminAuthInterceptor adminAuthInterceptor;

	@Autowired
	ApiLoggingInterceptor apiLoggingInterceptor;

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
//		registry
//			.addViewController("/")
//			.setViewName("forward:/index.html");
	}


	
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
//		configurer.setUseSuffixPatternMatch(false);
	}

	// CORS는 SimpleCorsFilter에서 smw.service-domain 기준으로 처리 (WebConfig CORS 제거 시 *+credentials 충돌 방지)
	// @Override addCorsMappings 제거

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
//		registry
//			.addResourceHandler("/resources/**")
//			.addResourceLocations("classpath:/WEB-INF/resources/");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		WebMvcConfigurer.super.addInterceptors(registry);
		
		// 일반용: 세션 주입 + @RequireLogin 분기
		// RTA/배치 API는 인증 인터셉터를 타지 않고 API 로깅만 수행한다.
		registry.addInterceptor(authSessionInterceptor)
		        .addPathPatterns("/api/v1/**")
		        .excludePathPatterns("/api/v1/rta/**", "/api/v1/batch/**");

		// 관리자용: @RequireAdmin 분기 (ROLE_ADMIN 권한 체크)
		// 배치 API는 운영상 별도 보호를 전제로 인터셉터 적용에서 제외한다.
		registry.addInterceptor(adminAuthInterceptor)
		        .addPathPatterns("/api/v1/**")
		        .excludePathPatterns("/api/v1/rta/**", "/api/v1/batch/**");

		// API 로깅 인터셉터: 모든 API에 적용 (인증 필요 없이 로깅만 수행)
		registry.addInterceptor(apiLoggingInterceptor)
		        .addPathPatterns("/api/v1/**");
	}
	
	@Bean
	public RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) httpConnectTimeoutMs);
		factory.setReadTimeout((int) httpReadTimeoutMs);
		return new RestTemplate(factory);
	}

}

