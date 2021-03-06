/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.shared.metrics;

import io.pravega.common.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test for Stats provider.
 */
@Slf4j
public class MetricsProviderTest {

    private final StatsLogger statsLogger = MetricsProvider.createStatsLogger("testStatsLogger");
    private final DynamicLogger dynamicLogger = MetricsProvider.getDynamicLogger();

    @Before
    public void setUp() {
        MetricsProvider.initialize(MetricsConfig.builder()
                                                .with(MetricsConfig.ENABLE_STATISTICS, true)
                                                .build());
    }

    /**
     * Test Event and Value registered and worked well with OpStats.
     */
    @Test
    public void testOpStatsData() {
        Timer startTime = new Timer();
        OpStatsLogger opStatsLogger = statsLogger.createStats("testOpStatsLogger");
        // register 2 event: 1 success, 1 fail.
        opStatsLogger.reportSuccessEvent(startTime.getElapsed());
        opStatsLogger.reportFailEvent(startTime.getElapsed());
        opStatsLogger.reportSuccessValue(startTime.getElapsedMillis());
        opStatsLogger.reportFailValue(startTime.getElapsedMillis());

        opStatsLogger.reportSuccessValue(1);
        opStatsLogger.reportFailValue(1);
        opStatsLogger.reportSuccessValue(1);

        OpStatsData statsData = opStatsLogger.toOpStatsData();
        // 2 = 2 event + 2 value
        assertEquals(4, statsData.getNumSuccessfulEvents());
        assertEquals(3, statsData.getNumFailedEvents());
    }

    /**
     * Test counter registered and  worked well with StatsLogger.
     */
    @Test
    public void testCounter() {
        Counter testCounter = statsLogger.createCounter("testCounter");
        testCounter.add(17);
        assertEquals(17, testCounter.get());

        // test dynamic counter
        int sum = 0;
        for (int i = 1; i < 10; i++) {
            sum += i;
            dynamicLogger.incCounterValue("dynamicCounter", i);
            assertEquals(sum, MetricsProvider.METRIC_REGISTRY.getCounters().get("pravega.dynamicCounter.Counter").getCount());
        }
    }

    /**
     * Test Meter registered and  worked well with StatsLogger.
     */
    @Test
    public void testMeter() {
        Meter testMeter = statsLogger.createMeter("testMeter");
        testMeter.recordEvent();
        testMeter.recordEvent();
        assertEquals(2, testMeter.getCount());
        testMeter.recordEvents(27);
        assertEquals(29, testMeter.getCount());

        // test dynamic meter
        int sum = 0;
        for (int i = 1; i < 10; i++) {
            sum += i;
            dynamicLogger.recordMeterEvents("dynamicMeter", i);
            assertEquals(sum, MetricsProvider.METRIC_REGISTRY.getMeters().get("pravega.dynamicMeter.Meter").getCount());
        }
    }

    /**
     * Test gauge registered and  worked well with StatsLogger.
     */
    @Test
    public void testGauge() {
        AtomicInteger value = new AtomicInteger(1);
        statsLogger.registerGauge("testGauge", value::get);

        for (int i = 1; i < 10; i++) {
            value.set(i);
            dynamicLogger.reportGaugeValue("dynamicGauge", i);
            assertEquals(i, MetricsProvider.METRIC_REGISTRY.getGauges().get("pravega.testStatsLogger.testGauge").getValue());
            assertEquals(i, MetricsProvider.METRIC_REGISTRY.getGauges().get("pravega.dynamicGauge.Gauge").getValue());
        }
    }

    /**
     * Test that we can transition from stats enabled, to disabled, to enabled.
     */
    @Test
    public void testMultipleInitialization() {
        MetricsConfig config = MetricsConfig.builder()
                                            .with(MetricsConfig.ENABLE_STATISTICS, false)
                                            .build();
        MetricsProvider.initialize(config);
        statsLogger.createCounter("counterDisabled");

        assertEquals(null, MetricsProvider.METRIC_REGISTRY.getCounters().get("counterDisabled"));

        config = MetricsConfig.builder()
                              .with(MetricsConfig.ENABLE_STATISTICS, true)
                              .build();
        MetricsProvider.initialize(config);
        statsLogger.createCounter("counterEnabled");

        Assert.assertNotNull(
                MetricsProvider.METRIC_REGISTRY.getCounters().get("pravega.testStatsLogger.counterEnabled"));
    }

    /**
     * Test that we can transition from stats enabled, to disabled, to enabled.
     */
    @Test
    public void testContinuity() {
        statsLogger.createCounter("continuity-counter");
        MetricsConfig config = MetricsConfig.builder()
                                            .with(MetricsConfig.ENABLE_STATISTICS, false)
                                            .build();
        MetricsProvider.initialize(config);

        Assert.assertNotNull(null,
                MetricsProvider.METRIC_REGISTRY.getCounters().get("pravega.testStatsLogger.continuity-counter"));
    }

    /**
     * Test transition back to null provider.
     */
    @Test
    public void testTransitionBackToNullProvider() {
        MetricsConfig config = MetricsConfig.builder()
                                            .with(MetricsConfig.ENABLE_STATISTICS, false)
                                            .build();
        MetricsProvider.initialize(config);

        Counter counter = statsLogger.createCounter("continuity-counter");
        counter.add(1L);
        assertEquals(0L, counter.get());

        config = MetricsConfig.builder()
                              .with(MetricsConfig.ENABLE_STATISTICS, true)
                              .build();
        MetricsProvider.initialize(config);

        counter.add(1L);
        assertEquals(1L, counter.get());
    }
}
