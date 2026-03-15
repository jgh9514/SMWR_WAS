package com.smw.api;

import javax.servlet.http.HttpSessionListener;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import com.sysconf.config.SessionListener;

@SpringBootApplication(scanBasePackages = { "com.smw", "com.sysconf", "com.cf" })
@EnableCaching
@MapperScan({ "com.smw", "com.cf", "com.sysconf" })
public class ApplicationApi extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(ApplicationApi.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(ApplicationApi.class);
	}

	@Bean
	public HttpSessionListener httpSessionListener() {
		return new SessionListener();
	}
}
