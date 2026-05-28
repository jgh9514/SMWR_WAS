package com.sysconf.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Grafana Cloud Loki OTLP 로그 라인 한도(256KB) 이하로 메시지·스택을 잘라낸다.
 */
public final class LogPayloadTrimmer {

    /** UTF-8 바이트 기준 — Loki 262144 한도 대비 여유 */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 200 * 1024;
    public static final int DEFAULT_MAX_STACK_BYTES = 24 * 1024;
    public static final int DEFAULT_MAX_STACK_LINES = 24;

    private static final String TRUNC_SUFFIX = "… [TRUNCATED: over OTLP log line limit]";

    private LogPayloadTrimmer() {
    }

    public static String truncateUtf8(String s, int maxBytes) {
        if (s == null) {
            return null;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return s;
        }
        int budget = Math.max(0, maxBytes - TRUNC_SUFFIX.getBytes(StandardCharsets.UTF_8).length);
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            int b = s.substring(0, mid).getBytes(StandardCharsets.UTF_8).length;
            if (b <= budget) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, lo) + TRUNC_SUFFIX;
    }

    public static String summarizeThrowable(Throwable t) {
        return summarizeThrowable(t, DEFAULT_MAX_STACK_LINES, DEFAULT_MAX_STACK_BYTES);
    }

    public static String summarizeThrowable(Throwable t, int maxLines, int maxBytes) {
        if (t == null) {
            return "";
        }
        StringWriter sw = new StringWriter(512);
        PrintWriter pw = new PrintWriter(sw);
        pw.println(t.getClass().getSimpleName() + ": " + safeMessage(t.getMessage()));
        StackTraceElement[] stack = t.getStackTrace();
        int lines = Math.min(stack.length, Math.max(1, maxLines));
        for (int i = 0; i < lines; i++) {
            pw.println("\tat " + stack[i]);
        }
        if (stack.length > lines) {
            pw.println("\t... " + (stack.length - lines) + " more");
        }
        pw.flush();
        return truncateUtf8(sw.toString(), maxBytes);
    }

    private static String safeMessage(String msg) {
        if (msg == null) {
            return "";
        }
        return truncateUtf8(msg.replaceAll("(?i)(password|passwd|pwd|token|secret|key)[=:\\s]+\\S+", "$1=[REDACTED]"), 512);
    }

    /** MyBatis 인라인 SQL 로그용 — 컬렉션 리터럴 요약 */
    public static String formatCollectionForSqlLog(Collection<?> coll) {
        if (coll == null) {
            return "NULL";
        }
        int n = coll.size();
        int show = Math.min(n, 32);
        StringBuilder sb = new StringBuilder(show * 12 + 48);
        sb.append('(');
        int i = 0;
        for (Object item : coll) {
            if (i >= show) {
                break;
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item);
            i++;
        }
        if (n > show) {
            sb.append(",… /* total=").append(n).append(" */");
        }
        sb.append(')');
        return sb.toString();
    }
}
