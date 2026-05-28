package com.sysconf.logging;

import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * LoggerConfig 필터 — 초대형 로그는 모든 Appender(OTEL Java agent 포함)로 전달하지 않는다.
 * Grafana Loki 256KB 한도 초과 시 OTLP 400 반복을 막기 위함.
 */
@Plugin(name = "OtelExportSizeCapFilter", category = "Core", elementType = Filter.ELEMENT_TYPE, printObject = true)
public final class OtelExportSizeCapFilter extends AbstractFilter {

    private final int maxBytes;

    private OtelExportSizeCapFilter(int maxBytes, Result onMatch, Result onMismatch) {
        super(onMatch, onMismatch);
        this.maxBytes = maxBytes;
    }

    @PluginFactory
    public static OtelExportSizeCapFilter createFilter(
            @PluginAttribute(value = "maxBytes", defaultInt = 200 * 1024) int maxBytes,
            @PluginAttribute(value = "onMatch", defaultString = "DENY") Result onMatch,
            @PluginAttribute(value = "onMismatch", defaultString = "NEUTRAL") Result onMismatch) {
        return new OtelExportSizeCapFilter(maxBytes, onMatch, onMismatch);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) {
            return onMismatch;
        }
        if (estimateUtf8Bytes(event) > maxBytes) {
            return onMatch;
        }
        return onMismatch;
    }

    private static int estimateUtf8Bytes(LogEvent event) {
        int size = 0;
        if (event.getMessage() != null) {
            String msg = event.getMessage().getFormattedMessage();
            if (msg != null) {
                size += msg.getBytes(StandardCharsets.UTF_8).length;
            }
        }
        Throwable t = event.getThrown();
        if (t != null) {
            size += LogPayloadTrimmer.summarizeThrowable(t).getBytes(StandardCharsets.UTF_8).length;
        }
        return size;
    }
}
