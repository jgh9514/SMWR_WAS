package com.smw.admin.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GrafanaEmbedConfigResponse {
	boolean enabled;
	String message;
	List<GrafanaDashboardEmbedItem> dashboards;
}
