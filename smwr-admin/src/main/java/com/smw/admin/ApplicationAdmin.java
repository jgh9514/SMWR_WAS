package com.smw.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = { "com.smw.admin", "com.smw.monster", "com.smw.guild", "com.smw.common", "com.admin", "com.sysconf", "com.cf" })
@EnableAsync
@MapperScan({ "com.smw.monster", "com.admin", "com.sysconf", "com.cf" })
public class ApplicationAdmin {

	public static void main(String[] args) {
		SpringApplication.run(ApplicationAdmin.class, args);
	}
}
