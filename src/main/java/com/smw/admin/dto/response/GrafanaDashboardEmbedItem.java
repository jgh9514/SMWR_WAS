package com.smw.admin.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GrafanaDashboardEmbedItem {
	String id;
	String title;
	String description;
	/** same-origin 프록시 iframe src (토큰 미포함) */
	String embedUrl;
	/** Grafana Cloud 원본 URL (새 탭) */
	String externalUrl;
}
