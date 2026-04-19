package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * RTA 대시보드 설정. 현재 API는 집계 테이블·배치 스냅만 사용한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.dashboard")
public class RtaDashboardProperties {
}
