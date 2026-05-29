package com.smw.admin.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "smw.grafana.cloud")
public class GrafanaCloudProperties {

	private boolean enabled = false;

	/** Grafana Cloud 스택 URL (예: https://yourstack.grafana.net) */
	private String baseUrl = "";

	/** Cloud Access Policy 토큰 (glsa_...) — 환경변수·Secret만 */
	private String accessToken = "";

	private int connectTimeoutMs = 10_000;

	private int readTimeoutMs = 30_000;

	private List<Dashboard> dashboards = new ArrayList<>();

	@Data
	public static class Dashboard {
		private String id = "";
		private String title = "";
		private String description = "";
		/** Grafana dashboard uid */
		private String uid = "";
		/** URL slug (없으면 uid만 사용) */
		private String slug = "";
	}

	public boolean isConfigured() {
		return enabled
				&& baseUrl != null && !baseUrl.isBlank()
				&& accessToken != null && !accessToken.isBlank();
	}

	public String normalizedBaseUrl() {
		if (baseUrl == null || baseUrl.isBlank()) {
			return "";
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public static final String PROXY_PREFIX = "/api/v1/admin/grafana/proxy";

}
