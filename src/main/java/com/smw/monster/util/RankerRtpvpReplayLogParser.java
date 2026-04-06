package com.smw.monster.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 프론트 extractRankerReplayItemsFromLogText 와 동일 규칙으로
 * 프록시/NDJSON 로그에서 RTA 리플레이 객체를 추출한다.
 * <p>
 * JSON 필드명 {@code ranker_replay_list}, {@code replay_list} 는 게임/Exporter API 페이로드 키이며 DB 테이블명이 아니다.
 */
public final class RankerRtpvpReplayLogParser {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private RankerRtpvpReplayLogParser() {
	}

	public static String normalizeRidKey(Object rid) {
		if (rid == null) {
			return "";
		}
		if (rid instanceof Number) {
			return String.valueOf(((Number) rid).longValue());
		}
		String s = String.valueOf(rid).trim();
		if (s.isEmpty()) {
			return "";
		}
		try {
			double d = Double.parseDouble(s);
			if (Double.isFinite(d) && s.matches("-?\\d+(\\.\\d+)?")) {
				return String.valueOf((long) d);
			}
		} catch (NumberFormatException ignored) {
		}
		return s;
	}

	public static List<Map<String, Object>> extractReplayItemsFromLogText(String text) {
		List<Map<String, Object>> results = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		String[] lines = text.split("\\r?\\n");

		for (String line : lines) {
			String t = line.trim();
			if (!t.contains("{")) {
				continue;
			}
			List<String> chunks = splitConcatenatedJsonObjects(t);
			if (chunks.isEmpty() && t.startsWith("{") && t.endsWith("}")) {
				chunks = List.of(t);
			}
			for (String jsonStr : chunks) {
				try {
					JsonNode data = MAPPER.readTree(jsonStr);
					mergeFromRtaReplayResponse(data, results, seen);
				} catch (JsonProcessingException ignored) {
				}
			}
		}

		int lineIdx = 0;
		while (lineIdx < lines.length) {
			if (!isTrackedRtaReplayApiCommandLine(lines[lineIdx])) {
				lineIdx++;
				continue;
			}
			int nextCmd = findNextTrackedRtaReplayApiCommandLine(lines, lineIdx + 1);
			int searchEnd = nextCmd >= 0 ? nextCmd : lines.length;
			int parsedEndLine = lineIdx;

			for (int j = lineIdx + 1; j < searchEnd; j++) {
				String L = lines[j].trim();
				if (!"Response:".equals(L) && !L.startsWith("Response:")) {
					continue;
				}

				int k = j + 1;
				while (k < lines.length && lines[k].trim().isEmpty()) {
					k++;
				}
				if (k >= lines.length) {
					break;
				}

				String single = lines[k].trim();
				if (single.startsWith("{")) {
					try {
						JsonNode data = MAPPER.readTree(single);
						mergeFromRtaReplayResponse(data, results, seen);
						parsedEndLine = k;
					} catch (JsonProcessingException e) {
						BalancedJson multi = extractBalancedJsonFromLineIndex(lines, k);
						if (multi != null) {
							try {
								JsonNode data = MAPPER.readTree(multi.text);
								mergeFromRtaReplayResponse(data, results, seen);
								parsedEndLine = multi.endLine;
							} catch (JsonProcessingException ignored) {
							}
						}
					}
				} else {
					BalancedJson multi = extractBalancedJsonFromLineIndex(lines, k);
					if (multi != null) {
						try {
							JsonNode data = MAPPER.readTree(multi.text);
							mergeFromRtaReplayResponse(data, results, seen);
							parsedEndLine = multi.endLine;
						} catch (JsonProcessingException ignored) {
						}
					}
				}
				break;
			}
			lineIdx = Math.max(lineIdx + 1, parsedEndLine + 1);
		}

		return results;
	}

	private static void mergeFromRtaReplayResponse(JsonNode data, List<Map<String, Object>> out, Set<String> seen) {
		if (data == null || !data.isObject()) {
			return;
		}
		JsonNode cmd = data.get("command");
		String command = cmd != null && cmd.isTextual() ? cmd.asText() : "";
		if ("getRankerRtpvpReplayList".equals(command) && data.has("ranker_replay_list")) {
			mergeReplayArrayIntoResults(data.get("ranker_replay_list"), out, seen);
		} else if ("getRtpvpRatingReplayList".equals(command) && data.has("replay_list")) {
			mergeReplayArrayIntoResults(data.get("replay_list"), out, seen);
		}
	}

	private static void mergeReplayArrayIntoResults(JsonNode list, List<Map<String, Object>> out, Set<String> seen) {
		if (list == null || !list.isArray()) {
			return;
		}
		for (JsonNode o : list) {
			if (o == null || !o.isObject()) {
				continue;
			}
			JsonNode ridNode = o.get("rid");
			Object ridVal = ridNode != null && ridNode.isNumber() ? ridNode.numberValue()
					: ridNode != null && ridNode.isTextual() ? ridNode.asText() : null;
			String key = normalizeRidKey(ridVal);
			if (key.isEmpty() || seen.contains(key)) {
				continue;
			}
			seen.add(key);
			Map<String, Object> m = MAPPER.convertValue(o, MAP_TYPE);
			out.add(m);
		}
	}

	private static List<String> splitConcatenatedJsonObjects(String line) {
		String t = line.trim();
		if (t.isEmpty() || t.charAt(0) != '{') {
			return List.of();
		}
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		boolean inString = false;
		boolean escape = false;
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (inString) {
				if (escape) {
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
				continue;
			}
			if (c == '{') {
				if (depth == 0) {
					start = i;
				}
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					parts.add(t.substring(start, i + 1));
				}
			}
		}
		return parts;
	}

	private static final class BalancedJson {
		final String text;
		final int endLine;

		BalancedJson(String text, int endLine) {
			this.text = text;
			this.endLine = endLine;
		}
	}

	private static BalancedJson extractBalancedJsonFromLineIndex(String[] lines, int startLine) {
		int depth = 0;
		StringBuilder buf = new StringBuilder();
		boolean started = false;
		boolean inString = false;
		boolean escape = false;

		for (int i = startLine; i < lines.length; i++) {
			String line = lines[i];
			for (int p = 0; p < line.length(); p++) {
				char c = line.charAt(p);
				if (inString) {
					if (escape) {
						escape = false;
					} else if (c == '\\') {
						escape = true;
					} else if (c == '"') {
						inString = false;
					}
					continue;
				}
				if (c == '"') {
					inString = true;
					continue;
				}
				if (c == '{') {
					depth++;
					started = true;
				} else if (c == '}') {
					depth--;
				}
			}
			if (buf.length() > 0) {
				buf.append('\n');
			}
			buf.append(line);
			if (started && depth == 0) {
				String t = buf.toString().trim();
				if (t.startsWith("{")) {
					try {
						MAPPER.readTree(t);
						return new BalancedJson(t, i);
					} catch (JsonProcessingException e) {
						return null;
					}
				}
			}
			if (depth < 0) {
				return null;
			}
		}
		return null;
	}

	private static boolean isTrackedRtaReplayApiCommandLine(String line) {
		String t = line.trim();
		if (!t.startsWith("API Command:")) {
			return false;
		}
		return t.contains("getRankerRtpvpReplayList") || t.contains("getRtpvpRatingReplayList");
	}

	private static int findNextTrackedRtaReplayApiCommandLine(String[] lines, int fromIndex) {
		for (int x = fromIndex; x < lines.length; x++) {
			if (isTrackedRtaReplayApiCommandLine(lines[x])) {
				return x;
			}
		}
		return -1;
	}
}
