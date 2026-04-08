package com.smw.rta.support;

import java.util.Set;

/**
 * RTA 매치 목록·통계의 세부 티어 키 — 프론트 {@code getRtaTierShortLabel}·대시보드 tier_key 와 동일.
 * Ch1~G3 (레전드 L 제외).
 */
public final class RtaTierKeyUtil {

    private static final Set<String> ALLOWED = Set.of(
            "Ch1", "Ch2", "Ch3",
            "F1", "F2", "F3",
            "C1", "C2", "C3",
            "P1", "P2", "P3",
            "G1", "G2", "G3");

    private RtaTierKeyUtil() {
    }

    /** 허용 키만 반환, 그 외·null·빈 문자열은 null (필터 없음) */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return ALLOWED.contains(t) ? t : null;
    }
}
