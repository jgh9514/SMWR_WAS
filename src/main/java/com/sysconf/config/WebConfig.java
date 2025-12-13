package com.sysconf.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sysconf.interceptor.AuthInterceptor;
import com.sysconf.interceptor.ApiLoggingInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Autowired
	AuthInterceptor authInterceptor;
	
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

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/v1/**")
                .allowCredentials(true);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
//		registry
//			.addResourceHandler("/resources/**")
//			.addResourceLocations("classpath:/WEB-INF/resources/");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		WebMvcConfigurer.super.addInterceptors(registry);
		
		// API 로깅 인터셉터: 모든 API에 적용 (인증 필요 없이 로깅만 수행)
		registry.addInterceptor(apiLoggingInterceptor)
		        .addPathPatterns("/api/v1/**");
		
		// 인증 인터셉터: 인증이 필요한 경로에만 적용
		registry.addInterceptor(authInterceptor)
		        .addPathPatterns(
		        		"/api/v1/admin/**",      // 관리자 영역
		        		"/api/v1/siege/**"       // 점령전 관련
		        );
	}
	
	@Bean
	public RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10000);
		factory.setReadTimeout(10000);
		return new RestTemplate(factory);
	}

}

