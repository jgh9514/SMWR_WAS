package com.sysconf.rest;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예전 프론트/PWA가 요청하는 속성 PNG 경로를 속성별 SVG 아이콘으로 직접 응답한다.
 * 오래된 Service Worker precache 실패를 줄이기 위한 호환 레이어다.
 */
@RestController
public class LegacyStaticAssetController {

	private static final MediaType SVG_MEDIA_TYPE = MediaType.parseMediaType("image/svg+xml");

	private static final Map<String, String> LEGACY_ELEMENT_ICON_MAP = Map.of(
			"Fire_Icon.png", "fire",
			"Water_Icon.png", "water",
			"Wind_Icon.png", "wind",
			"Light_Icon.png", "light",
			"Dark_Icon.png", "dark");

	@GetMapping(value = "/images/{fileName:.+}", produces = "image/svg+xml")
	public ResponseEntity<String> renderLegacyElementIcon(@PathVariable String fileName) {
		String element = LEGACY_ELEMENT_ICON_MAP.get(fileName);
		if (element == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(SVG_MEDIA_TYPE);
		headers.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");
		headers.add("Pragma", "no-cache");

		return new ResponseEntity<>(buildElementSvg(element), headers, HttpStatus.OK);
	}

	private String buildElementSvg(String element) {
		switch (element) {
		case "fire":
			return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 48 48'>"
					+ "<circle cx='24' cy='24' r='22' fill='#fff5f5'/>"
					+ "<path fill='#ef4444' d='M25 7c-1 5-6 8-8 13-2 4-2 7-2 10 0 8 4 12 9 12s9-4 9-12c0-7-4-14-8-23z'/>"
					+ "<path fill='#fecaca' d='M24 20c-3 3-5 6-5 10 0 4 2 7 5 7s5-3 5-7c0-3-1-6-5-10z'/>"
					+ "</svg>";
		case "water":
			return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 48 48'>"
					+ "<circle cx='24' cy='24' r='22' fill='#eff6ff'/>"
					+ "<path fill='#3b82f6' d='M24 8c-6 8-10 14-10 20 0 7 4 12 10 12s10-5 10-12c0-6-4-12-10-20z'/>"
					+ "<path fill='#bfdbfe' d='M20 26c0 4 2 7 5 8-1 1-2 1-3 1-4 0-7-3-7-7 0-2 1-4 2-6 1 1 3 2 3 4z'/>"
					+ "</svg>";
		case "wind":
			return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 48 48'>"
					+ "<circle cx='24' cy='24' r='22' fill='#f0fdf4'/>"
					+ "<path fill='none' stroke='#22c55e' stroke-linecap='round' stroke-width='4' d='M12 20h16c4 0 6-2 6-5s-2-5-5-5c-3 0-5 2-5 5'/>"
					+ "<path fill='none' stroke='#22c55e' stroke-linecap='round' stroke-width='4' d='M10 26h22c4 0 6 2 6 5s-2 5-5 5c-3 0-5-2-5-5'/>"
					+ "<path fill='none' stroke='#86efac' stroke-linecap='round' stroke-width='3' d='M14 32h10'/>"
					+ "</svg>";
		case "light":
			return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 48 48'>"
					+ "<circle cx='24' cy='24' r='22' fill='#fffbeb'/>"
					+ "<circle cx='24' cy='24' r='8' fill='#f59e0b'/>"
					+ "<g stroke='#fcd34d' stroke-linecap='round' stroke-width='3'>"
					+ "<path d='M24 8v6'/><path d='M24 34v6'/><path d='M8 24h6'/><path d='M34 24h6'/>"
					+ "<path d='M13 13l4 4'/><path d='M31 31l4 4'/><path d='M13 35l4-4'/><path d='M31 17l4-4'/>"
					+ "</g></svg>";
		case "dark":
			return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 48 48'>"
					+ "<circle cx='24' cy='24' r='22' fill='#faf5ff'/>"
					+ "<path fill='#8b5cf6' d='M30 10c-7 1-12 7-12 14 0 7 5 13 12 14-3 2-6 3-9 3-8 0-14-7-14-15S13 11 21 11c3 0 6 0 9-1z' transform='translate(8 -1)'/>"
					+ "<circle cx='30' cy='18' r='2' fill='#c4b5fd'/>"
					+ "<circle cx='34' cy='24' r='1.5' fill='#c4b5fd'/>"
					+ "</svg>";
		default:
			return "";
		}
	}
}
