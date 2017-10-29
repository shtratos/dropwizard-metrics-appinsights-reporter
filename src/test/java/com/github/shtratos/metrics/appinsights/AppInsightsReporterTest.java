package com.github.shtratos.metrics.appinsights;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AppInsightsReporterTest {

    public static void main(String[] args) throws Exception {
        new AppInsightsReporterTest().testReporter();

    }

    @Test
    public void testReporter() throws Exception {
        // given
        MetricRegistry registry = new MetricRegistry();

        TelemetryConfiguration.getActive().setInstrumentationKey("");
        TelemetryConfiguration.getActive().getChannel().setDeveloperMode(true);
        final TelemetryClient telemetryClient = new TelemetryClient(TelemetryConfiguration.getActive());

        final AppInsightsReporter reporter = AppInsightsReporter.forRegistry(registry)
                .name("test App Insights reporter")
                .filter(MetricFilter.ALL)
                .rateUnit(TimeUnit.SECONDS)
                .durationUnit(TimeUnit.MILLISECONDS)
                .metricNamePrefix("foo/")
                .telemetryClient(telemetryClient)
                .build();


        final AtomicInteger gaugeInteger = new AtomicInteger();

        registry.register(name("gauge"), (Gauge<Integer>) gaugeInteger::get);

        final Counter counter = registry.counter(name("counter"));

        final Histogram histogram = registry.histogram(name("histogram"));

        final Meter meter = registry.meter(name("meter"));

        final Timer timer = registry.timer(name("timer"));

        reporter.start(10, TimeUnit.SECONDS);

        ScheduledExecutorService svc = Executors.newScheduledThreadPool(1);

        final Random random = new Random();

        svc.scheduleAtFixedRate(() -> {
            System.out.println("Updating");
            gaugeInteger.incrementAndGet();
            counter.inc();
            histogram.update(random.nextInt(10));
            meter.mark();
            timer.update(random.nextInt(10), TimeUnit.MILLISECONDS);
        }, 0, 1, TimeUnit.SECONDS);
    }

    private static String name(String s) {
        return MetricRegistry.name(AppInsightsReporterTest.class, s);
    }
}
