package com.smw.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.smw.admin", "com.smw.monster", "com.admin", "com.sysconf" })
@MapperScan({ "com.smw.monster", "com.admin", "com.sysconf" })
public class ApplicationAdmin {

	public static void main(String[] args) {
		SpringApplication.run(ApplicationAdmin.class, args);
	}
}
