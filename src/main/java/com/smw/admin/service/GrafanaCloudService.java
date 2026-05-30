package com.smw.admin.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.admin.config.GrafanaCloudProperties;
import com.smw.admin.config.GrafanaCloudProperties.Dashboard;
import com.smw.admin.dto.response.GrafanaDashboardEmbedItem;
import com.smw.admin.dto.response.GrafanaEmbedConfigResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrafanaCloudService {

	private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
			"transfer-encoding",
			"connection",
			"keep-alive",
			"content-encoding",
			"x-frame-options",
			"content-security-policy",
			"content-security-policy-report-only"
	);

	private final GrafanaCloudProperties properties;
	private final ObjectMapper objectMapper;

	private HttpClient httpClient;

	private volatile GrafanaEmbedConfigResponse embedConfigCache;
	private volatile long embedConfigCachedAtMs;

	private static final long EMBED_CONFIG_CACHE_TTL_MS = 5 * 60_000L;

	@PostConstruct
	void initHttpClient() {
		httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(Math.max(1_000, properties.getConnectTimeoutMs())))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	public GrafanaEmbedConfigResponse getEmbedConfig() {
		long now = System.currentTimeMillis();
		GrafanaEmbedConfigResponse cached = embedConfigCache;
		if (cached != null && now - embedConfigCachedAtMs < EMBED_CONFIG_CACHE_TTL_MS) {
			return cached;
		}

		GrafanaEmbedConfigResponse fresh = loadEmbedConfig();
		embedConfigCache = fresh;
		embedConfigCachedAtMs = now;
		return fresh;
	}

	private GrafanaEmbedConfigResponse loadEmbedConfig() {
		if (!properties.isConfigured()) {
			return GrafanaEmbedConfigResponse.builder()
					.enabled(false)
					.message("Grafana Cloud가 비활성화되었거나 base-url·access-token이 설정되지 않았습니다.")
					.dashboards(Collections.emptyList())
					.build();
		}

		List<GrafanaDashboardEmbedItem> items = new ArrayList<>();
		for (Dashboard dash : properties.getDashboards()) {
			GrafanaDashboardEmbedItem item = toEmbedItem(dash);
			if (item != null) {
				items.add(item);
			}
		}

		if (items.isEmpty()) {
			items.addAll(discoverDashboardsFromApi());
		}

		if (items.isEmpty()) {
			return GrafanaEmbedConfigResponse.builder()
					.enabled(true)
					.message("대시보드 uid가 설정되지 않았습니다. smw.grafana.cloud.dashboards 또는 Grafana search API를 확인하세요.")
					.dashboards(Collections.emptyList())
					.build();
		}

		return GrafanaEmbedConfigResponse.builder()
				.enabled(true)
				.message("")
				.dashboards(items)
				.build();
	}

	public void proxy(String grafanaPath, String queryString, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (!properties.isConfigured()) {
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Grafana Cloud not configured");
			return;
		}

		String path = grafanaPath == null || grafanaPath.isBlank() ? "/" : grafanaPath;
		String targetUrl = properties.normalizedBaseUrl() + path;
		if (queryString != null && !queryString.isBlank()) {
			targetUrl += "?" + queryString;
		}

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(targetUrl))
				.timeout(Duration.ofMillis(Math.max(5_000, properties.getReadTimeoutMs())))
				.header("Authorization", "Bearer " + properties.getAccessToken().trim())
				.header("Accept", request.getHeader("Accept") != null ? request.getHeader("Accept") : "*/*");

		String method = request.getMethod();
		if ("HEAD".equalsIgnoreCase(method)) {
			builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
		} else {
			builder.GET();
		}

		try {
			HttpResponse<InputStream> upstream = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
			int status = upstream.statusCode();
			response.setStatus(status);

			upstream.headers().map().forEach((name, values) -> {
				if (SKIP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
					return;
				}
				for (String value : values) {
					if ("location".equalsIgnoreCase(name)) {
						response.addHeader(name, rewriteLocation(value));
					} else {
						response.addHeader(name, value);
					}
				}
			});

			if ("HEAD".equalsIgnoreCase(method)) {
				return;
			}

			String contentType = upstream.headers().firstValue("content-type").orElse("");
			byte[] body = upstream.body().readAllBytes();
			body = rewriteBodyIfNeeded(body, contentType);

			response.setContentLength(body.length);
			try (OutputStream out = response.getOutputStream()) {
				out.write(body);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("[grafana-proxy] interrupted path={}", path, e);
			response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Grafana proxy interrupted");
		} catch (Exception e) {
			log.error("[grafana-proxy] failed path={} target={}", path, targetUrl, e);
			response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Grafana proxy failed");
		}
	}

	private GrafanaDashboardEmbedItem toEmbedItem(Dashboard dash) {
		if (dash == null || dash.getUid() == null || dash.getUid().isBlank()) {
			return null;
		}
		String slug = dash.getSlug() != null && !dash.getSlug().isBlank() ? dash.getSlug() : dash.getUid();
		String path = "/d/" + dash.getUid() + "/" + slug;
		String query = "orgId=1&kiosk=1&theme=dark&from=now-6h&to=now";
		String embedUrl = GrafanaCloudProperties.PROXY_PREFIX + path + "?" + query;
		String externalUrl = properties.normalizedBaseUrl() + path + "?" + query;

		return GrafanaDashboardEmbedItem.builder()
				.id(dash.getId() != null && !dash.getId().isBlank() ? dash.getId() : dash.getUid())
				.title(dash.getTitle() != null && !dash.getTitle().isBlank() ? dash.getTitle() : dash.getUid())
				.description(dash.getDescription() != null ? dash.getDescription() : "")
				.embedUrl(embedUrl)
				.externalUrl(externalUrl)
				.build();
	}

	private List<GrafanaDashboardEmbedItem> discoverDashboardsFromApi() {
		try {
			String url = properties.normalizedBaseUrl() + "/api/search?type=dash-db&limit=10";
			HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
					.header("Authorization", "Bearer " + properties.getAccessToken().trim())
					.GET()
					.build();
			HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				log.warn("[grafana] search API status={}", res.statusCode());
				return Collections.emptyList();
			}

			JsonNode root = objectMapper.readTree(res.body());
			if (!root.isArray()) {
				return Collections.emptyList();
			}

			List<GrafanaDashboardEmbedItem> items = new ArrayList<>();
			for (JsonNode node : root) {
				String uid = node.path("uid").asText("");
				if (uid.isBlank()) {
					continue;
				}
				String title = node.path("title").asText(uid);
				String uri = node.path("uri").asText("");
				String slug = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uid;
				Dashboard d = new Dashboard();
				d.setId(uid);
				d.setUid(uid);
				d.setSlug(slug);
				d.setTitle(title);
				d.setDescription("Grafana Cloud search API");
				GrafanaDashboardEmbedItem item = toEmbedItem(d);
				if (item != null) {
					items.add(item);
				}
			}
			return items;
		} catch (Exception e) {
			log.warn("[grafana] dashboard search failed: {}", e.toString());
			return Collections.emptyList();
		}
	}

	private String rewriteLocation(String location) {
		if (location == null || location.isBlank()) {
			return location;
		}
		String base = properties.normalizedBaseUrl();
		String proxy = GrafanaCloudProperties.PROXY_PREFIX;
		if (location.startsWith(base)) {
			return proxy + location.substring(base.length());
		}
		return location;
	}

	private byte[] rewriteBodyIfNeeded(byte[] body, String contentType) {
		if (body == null || body.length == 0 || contentType == null) {
			return body;
		}
		String lower = contentType.toLowerCase(Locale.ROOT);
		boolean rewrite = lower.contains("text/html")
				|| lower.contains("javascript")
				|| lower.contains("text/css")
				|| lower.contains("application/json");
		if (!rewrite) {
			return body;
		}

		String base = properties.normalizedBaseUrl();
		String proxy = GrafanaCloudProperties.PROXY_PREFIX;
		String text = new String(body, StandardCharsets.UTF_8);

		Set<String> replacements = new LinkedHashSet<>();
		replacements.add(base);
		if (base.startsWith("https://")) {
			replacements.add(base.replace("https://", "http://"));
		}

		for (String from : replacements) {
			text = text.replace(from, proxy);
		}

		return text.getBytes(StandardCharsets.UTF_8);
	}
}
