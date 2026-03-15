package com.smw.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.smw.common.util.S3Service;

@Configuration
public class AdminConfig {

	@Bean
	public S3Service s3Service() {
		return new S3Service();
	}
}
