package com.smw;


import jakarta.servlet.http.HttpSessionListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.smw.monster.config.RtaExporterProperties;
import com.smw.rta.config.BatchLogProperties;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaDashboardProperties;
import com.smw.rta.config.RtaRankCutValidationProperties;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.config.RtaStagingMergeProperties;
import com.sysconf.config.SessionListener;

@SpringBootApplication(scanBasePackages = {"com.smw", "com.sysconf", "com.admin", "com.cf"})
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties({ RtaExporterProperties.class, RtaBatchProperties.class, RtaRawApplyProperties.class,
		RtaDashboardProperties.class, RtaStagingMergeProperties.class, RtaRankCutValidationProperties.class,
		BatchLogProperties.class })
@MapperScan({
		"com.smw.monster.mapper",
		"com.smw.rta.mapper",
		"com.smw.account.mapper",
		"com.smw.guild.mapper",
		"com.smw.admin.mapper",
		"com.admin.multilang.mapper",
		"com.admin.log.mapper",
		"com.admin.user.mapper",
		"com.admin.batch.mapper",
		"com.smw.monster.batch.queue",
		"com.cf.community.mapper",
		"com.cf.notification.mapper"
})
public class Application extends SpringBootServletInitializer {

	private static final Logger logger = LogManager.getLogger(Application.class);

	public static void main(String[] args) {
		logger.info("=== SMW Application Starting ===");
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
