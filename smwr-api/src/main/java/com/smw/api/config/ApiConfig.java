package com.smw.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.smw.common.util.S3Service;

@Configuration
public class ApiConfig {

	@Bean
	public S3Service s3Service() {
		return new S3Service();
	}
}
