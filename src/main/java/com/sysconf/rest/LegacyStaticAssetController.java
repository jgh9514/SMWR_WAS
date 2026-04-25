package com.sysconf.rest;

import java.net.URI;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예전 프론트/PWA가 요청하던 {@code /images/*_Icon.png} 를 그대로 두고,
 * 실제 정적 본문은 S3(CloudFront) {@code {cdn}/images/파일명} 으로 넘겨 응답하도록 리다이렉트한다.
 * 오래된 Service Worker·캐시가 WAS 경로로 조회해도 최종은 CDN 쪽 자산이 쓰인다.
 */
@RestController
public class LegacyStaticAssetController {

	/** S3/CloudFront에 동일 파일명으로 올라간 속성 아이콘(PNG)만 화이트리스트 */
	private static final Set<String> LEGACY_ELEMENT_ICON_NAMES = Set.of(
			"Fire_Icon.png",
			"Water_Icon.png",
			"Wind_Icon.png",
			"Light_Icon.png",
			"Dark_Icon.png");

	@Value("${smw.cdn.public-base-url:https://dyjduzi8vf2k4.cloudfront.net}")
	private String cdnPublicBaseUrl;

	@GetMapping(value = "/images/{fileName:.+}")
	public ResponseEntity<Void> redirectLegacyElementIcon(@PathVariable String fileName) {
		if (fileName == null || !LEGACY_ELEMENT_ICON_NAMES.contains(fileName)) {
			return ResponseEntity.notFound().build();
		}
		String base = cdnPublicBaseUrl != null ? cdnPublicBaseUrl.trim() : "";
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.isEmpty()) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		URI location = URI.create(base + "/images/" + fileName);
		return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
	}
}
