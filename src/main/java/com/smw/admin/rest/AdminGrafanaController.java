package com.smw.admin.rest;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.admin.config.GrafanaCloudProperties;
import com.smw.admin.dto.response.GrafanaEmbedConfigResponse;
import com.smw.admin.service.GrafanaCloudService;
import com.sysconf.annotation.RequireAdmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Grafana", description = "Grafana Cloud 프록시·임베드 (Access Policy 토큰 서버측만)")
@RequireAdmin
@RestController
@RequestMapping("/api/v1/admin/grafana")
@RequiredArgsConstructor
public class AdminGrafanaController {

	private final GrafanaCloudService grafanaCloudService;

	@Operation(summary = "Grafana 임베드 설정", description = "same-origin 프록시 URL 반환 (glsa 토큰 미노출)")
	@PostMapping("/embed-config")
	public ResponseEntity<GrafanaEmbedConfigResponse> embedConfig(@RequestBody(required = false) Map<String, Object> body) {
		return ResponseEntity.ok(grafanaCloudService.getEmbedConfig());
	}

	@Operation(summary = "Grafana Cloud HTTP 프록시", description = "iframe·에셋 요청 — Authorization Bearer glsa (서버 주입)")
	@GetMapping("/proxy/**")
	public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String uri = request.getRequestURI();
		String marker = GrafanaCloudProperties.PROXY_PREFIX;
		int idx = uri.indexOf(marker);
		String grafanaPath = idx >= 0 ? uri.substring(idx + marker.length()) : "/";
		if (grafanaPath.isEmpty()) {
			grafanaPath = "/";
		}
		grafanaCloudService.proxy(grafanaPath, request.getQueryString(), request, response);
	}
}
