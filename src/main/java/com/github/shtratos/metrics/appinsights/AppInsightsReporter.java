package com.github.shtratos.metrics.appinsights;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.microsoft.applicationinsights.TelemetryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * A reporter for Metrics that writes to Azure AppInsights
 */
@ThreadSafe
public final class AppInsightsReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(AppInsightsReporter.class);

    private final String metricNamePrefix;
    private final TelemetryClient telemetryClient;

    /**
     * Returns a new {@link Builder} for {@link AppInsightsReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link AppInsightsReporter}
     */
    public static AppInsightsReporter.Builder forRegistry(MetricRegistry registry) {
        return new AppInsightsReporter.Builder(registry);
    }

    /**
     * @param registry         metric registry to get metrics from
     * @param name             reporter name
     * @param filter           metric filter
     * @param rateUnit         unit for reporting rates
     * @param durationUnit     unit for reporting durations
     * @param metricNamePrefix prefix before the metric name used when naming App Insights metrics. Use "" if no prefix is
     *                         needed.
     * @param telemetryClient  telemetry client
     * @see ScheduledReporter#ScheduledReporter(MetricRegistry, String, MetricFilter, TimeUnit, TimeUnit)
     */
    private AppInsightsReporter(MetricRegistry registry, String name, MetricFilter filter,
                                TimeUnit rateUnit, TimeUnit durationUnit, String metricNamePrefix,
                                TelemetryClient telemetryClient) {
        super(registry, name, filter, rateUnit, durationUnit);
        this.metricNamePrefix = metricNamePrefix;
        this.telemetryClient = telemetryClient;

        LOG.info("Initialized AppInsightsReporter for registry with name '{}', filter of type '{}', rate unit {} , duration unit {} and name prefix '{}'",
                name, filter.getClass().getCanonicalName(), rateUnit.toString(), durationUnit.toString(), metricNamePrefix);
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        LOG.debug("Received report of {} gauges, {} counters, {} histograms, {} meters and {} timers",
                gauges.size(), counters.size(), histograms.size(), meters.size(), timers.size());

        for (Map.Entry<String, Gauge> gaugeEntry : gauges.entrySet()) {
            doGauge(gaugeEntry.getKey(), gaugeEntry.getValue());
        }

        for (Map.Entry<String, Counter> counterEntry : counters.entrySet()) {
            String name = counterEntry.getKey();
            Counter counter = counterEntry.getValue();
            record(name + "/count", counter.getCount());
        }

        for (Map.Entry<String, Histogram> histogramEntry : histograms.entrySet()) {
            String name = histogramEntry.getKey();
            Snapshot snapshot = histogramEntry.getValue().getSnapshot();

            Histogram metric = histogramEntry.getValue();
            doHistogramSnapshot(name, snapshot, metric);
        }

        for (Map.Entry<String, Meter> meterEntry : meters.entrySet()) {
            String name = meterEntry.getKey();
            Meter meter = meterEntry.getValue();
            doMetered(name, meter);
        }

        for (Map.Entry<String, Timer> timerEntry : timers.entrySet()) {
            Timer timer = timerEntry.getValue();
            String name = timerEntry.getKey();
            Snapshot snapshot = timer.getSnapshot();

            doTimerMetered(timer, name);
            doTimerSnapshot(timer, name, snapshot);
        }
    }

    private void doMetered(String name, Metered meter) {
        record(name + "/count", meter.getCount());
        record(name + "/meanRate/" + getRateUnit(), (float) convertRate(meter.getMeanRate()));
        record(name + "/1MinuteRate/" + getRateUnit(), (float) convertRate(meter.getOneMinuteRate()));
        record(name + "/5MinuteRate/" + getRateUnit(), (float) convertRate(meter.getFiveMinuteRate()));
        record(name + "/15MinuteRate/" + getRateUnit(), (float) convertRate(meter.getFifteenMinuteRate()));
    }

    private void doTimerMetered(Timer timer, String name) {
        doMetered(name, timer);
    }

    private void doHistogramSnapshot(String name, Snapshot snapshot, Histogram metric) {
        record(name + "/min", (float) convertDuration(snapshot.getMin()));
        record(name + "/max", (float) convertDuration(snapshot.getMax()));
        record(name + "/mean", (float) convertDuration(snapshot.getMean()));
        record(name + "/stdDev", (float) convertDuration(snapshot.getStdDev()));
        record(name + "/median", (float) convertDuration(snapshot.getMedian()));
        record(name + "/75th", (float) convertDuration(snapshot.get75thPercentile()));
        record(name + "/95th", (float) convertDuration(snapshot.get95thPercentile()));
        record(name + "/98th", (float) convertDuration(snapshot.get98thPercentile()));
        record(name + "/99th", (float) convertDuration(snapshot.get99thPercentile()));
        record(name + "/99.9th", (float) convertDuration(snapshot.get999thPercentile()));
    }

    private void doTimerSnapshot(Timer timer, String name, Snapshot snapshot) {
        String nameSuffix = "/" + getDurationUnit();
        record(name + "/min" + nameSuffix, (float) convertDuration(snapshot.getMin()));
        record(name + "/max" + nameSuffix, (float) convertDuration(snapshot.getMax()));
        record(name + "/mean" + nameSuffix, (float) convertDuration(snapshot.getMean()));
        record(name + "/stdDev" + nameSuffix, (float) convertDuration(snapshot.getStdDev()));
        record(name + "/median" + nameSuffix, (float) convertDuration(snapshot.getMedian()));
        record(name + "/75th" + nameSuffix, (float) convertDuration(snapshot.get75thPercentile()));
        record(name + "/95th" + nameSuffix, (float) convertDuration(snapshot.get95thPercentile()));
        record(name + "/98th" + nameSuffix, (float) convertDuration(snapshot.get98thPercentile()));
        record(name + "/99th" + nameSuffix, (float) convertDuration(snapshot.get99thPercentile()));
        record(name + "/99.9th" + nameSuffix, (float) convertDuration(snapshot.get999thPercentile()));
    }

    private void doGauge(String name, Gauge gauge) {
        Object gaugeValue = gauge.getValue();

        if (gaugeValue instanceof Number) {
            float n = ((Number) gaugeValue).floatValue();
            if (!Float.isNaN(n) && !Float.isInfinite(n)) {
                record(name, n);
            }
        }
    }

    private void record(String name, float value) {
        String fullMetricName = metricNamePrefix + name;
        LOG.trace("Reporting metric {} with value {}", fullMetricName, value);
        telemetryClient.trackMetric(fullMetricName, value);
    }

    public static final class Builder {
        private MetricRegistry registry;
        private String name;
        private MetricFilter filter;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private String metricNamePrefix;
        private TelemetryClient telemetryClient;

        public Builder(MetricRegistry registry) {
            this.registry = registry;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.metricNamePrefix = "";
            this.name = "App Insights reporter";
            this.filter = MetricFilter.ALL;
            this.telemetryClient = null;
        }

        /**
         * @param name reporter name
         * @return this
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @param filter metric filter
         * @return this
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * @param rateUnit unit for reporting rates
         * @return this
         */
        public Builder rateUnit(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * @param durationUnit unit for reporting durations
         * @return this
         */
        public Builder durationUnit(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * @param metricNamePrefix prefix before the metric name used when naming App Insights metrics. Use "" if no prefix
         *                         is needed.
         * @return this
         */
        public Builder metricNamePrefix(String metricNamePrefix) {
            this.metricNamePrefix = metricNamePrefix;
            return this;
        }

        /**
         * @param telemetryClient telemetry client for reporting metrics
         * @return this
         */
        public Builder telemetryClient(TelemetryClient telemetryClient) {
            this.telemetryClient = telemetryClient;
            return this;
        }

        public AppInsightsReporter build() {
            final TelemetryClient client = telemetryClient != null ? telemetryClient : new TelemetryClient();
            return new AppInsightsReporter(registry, name, filter, rateUnit, durationUnit,
                    metricNamePrefix, client);
        }
    }
}
