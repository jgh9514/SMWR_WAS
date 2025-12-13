package com.smw;


import javax.servlet.http.HttpSessionListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import com.sysconf.config.SessionListener;

@SpringBootApplication(scanBasePackages = "com.smw")
@EnableCaching
public class Application extends SpringBootServletInitializer {

	private static final Logger logger = LogManager.getLogger(Application.class);

	public static void main(String[] args) {
		logger.info("=== SMW Application Starting ===");
		logger.debug("Debug message test");
		logger.info("Info message test");
		logger.warn("Warning message test");
		logger.error("Error message test");
		SpringApplication.run(Application.class, args);
		logger.info("=== SMW Application Started Successfully ===");
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(Application.class);
	}
	
	@Bean
	public HttpSessionListener httpSessionListener() {
		return new SessionListener();
	}
	
}
