package com.smw.monster.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

@Component
public class SwarfarmSyncMetrics {

    private final MeterRegistry meterRegistry;

    public SwarfarmSyncMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    public SyncStats newStats() {
        return new SyncStats();
    }

    public Timer.Sample startTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }

    public void recordSummary(String entity, String result, SyncStats stats, Timer.Sample sample) {
        if (meterRegistry == null || stats == null) {
            return;
        }

        Tags resultTags = Tags.of(
                "entity", entity,
                "result", result
        );

        Counter.builder("smw.swarfarm.sync.runs")
                .description("Swarfarm sync run count")
                .tags(resultTags)
                .register(meterRegistry)
                .increment();

        recordItemCount(entity, "processed", stats.getProcessed());
        recordItemCount(entity, "saved", stats.getSaved());
        recordItemCount(entity, "skipped", stats.getSkipped());
        recordItemCount(entity, "failed", stats.getFailed());

        if (sample != null) {
            sample.stop(Timer.builder("smw.swarfarm.sync.duration")
                    .description("Swarfarm sync duration")
                    .tags(resultTags)
                    .register(meterRegistry));
        }
    }

    private void recordItemCount(String entity, String outcome, long count) {
        if (count <= 0) {
            return;
        }

        Counter.builder("smw.swarfarm.sync.items")
                .description("Swarfarm sync item count")
                .tags("entity", entity, "outcome", outcome)
                .register(meterRegistry)
                .increment(count);
    }

    public static class SyncStats {
        private long processed;
        private long saved;
        private long skipped;
        private long failed;

        public void addProcessed(long count) {
            this.processed += Math.max(0, count);
        }

        public void addSaved(long count) {
            this.saved += Math.max(0, count);
        }

        public void addSkipped(long count) {
            this.skipped += Math.max(0, count);
        }

        public void addFailed(long count) {
            this.failed += Math.max(0, count);
        }

        public long getProcessed() {
            return processed;
        }

        public long getSaved() {
            return saved;
        }

        public long getSkipped() {
            return skipped;
        }

        public long getFailed() {
            return failed;
        }
    }
}
