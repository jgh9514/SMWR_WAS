package com.sysconf.rest;

import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예전 프론트/PWA가 요청하는 속성 PNG 경로를 현재 아이콘 자산으로 우회한다.
 * 오래된 Service Worker precache 실패를 줄이기 위한 호환 레이어다.
 */
@RestController
public class LegacyStaticAssetController {

	private static final Set<String> LEGACY_ELEMENT_ICON_NAMES = Set.of(
			"Fire_Icon.png",
			"Water_Icon.png",
			"Wind_Icon.png",
			"Light_Icon.png",
			"Dark_Icon.png");

	@GetMapping("/images/{fileName:.+}")
	public ResponseEntity<Void> redirectLegacyElementIcon(@PathVariable String fileName) {
		if (!LEGACY_ELEMENT_ICON_NAMES.contains(fileName)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(URI.create("/icons/ci_active.png"));
		return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT);
	}
}
