package com.sysconf.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;

/**
 * 모든 Log4j2 이벤트 메시지·예외를 OTLP/Loki 한도 이하로 잘라 Grafana 400 을 방지한다.
 */
@Plugin(name = "TruncateRewritePolicy", category = "Core", elementType = "rewritePolicy", printObject = true)
public final class TruncateRewritePolicy implements RewritePolicy {

    @PluginFactory
    public static TruncateRewritePolicy createPolicy() {
        return new TruncateRewritePolicy();
    }

    @Override
    public LogEvent rewrite(LogEvent event) {
        if (event == null) {
            return null;
        }
        Message message = event.getMessage();
        String formatted = message != null ? message.getFormattedMessage() : "";
        StringBuilder combined = new StringBuilder(Math.min(formatted.length() + 256, 4096));
        combined.append(formatted);

        Throwable thrown = event.getThrown();
        if (thrown != null) {
            combined.append(" | ").append(LogPayloadTrimmer.summarizeThrowable(thrown));
        }

        String safe = LogPayloadTrimmer.truncateUtf8(combined.toString(), LogPayloadTrimmer.DEFAULT_MAX_MESSAGE_BYTES);
        return Log4jLogEvent.newBuilder(event)
                .setMessage(new SimpleMessage(safe))
                .setThrown(null)
                .build();
    }
}
