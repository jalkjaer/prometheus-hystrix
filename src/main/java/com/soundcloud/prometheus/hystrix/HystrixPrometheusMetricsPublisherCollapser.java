/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Implementation of {@link HystrixMetricsPublisherCollapser} using Prometheus Metrics.
 */
public class HystrixPrometheusMetricsPublisherCollapser implements HystrixMetricsPublisherCollapser {

    private static final String SUBSYSTEM = "hystrix_collapser";

    private final Map<String, String> labels;
    private final boolean exportProperties;

    private final HystrixCollapserMetrics metrics;
    private final HystrixCollapserProperties properties;
    private final PrometheusMetricsCollector collector;

    public HystrixPrometheusMetricsPublisherCollapser(
            PrometheusMetricsCollector collector, HystrixCollapserKey key, HystrixCollapserMetrics metrics,
            HystrixCollapserProperties properties, boolean exportProperties) {

        this.metrics = metrics;
        this.collector = collector;
        this.properties = properties;
        this.exportProperties = exportProperties;
        this.labels = Collections.singletonMap("collapser_name", key.name());
    }

    @Override
    public void initialize() {
        createCumulativeCountForEvent("count_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createCumulativeCountForEvent("count_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        createRollingCountForEvent("rolling_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createRollingCountForEvent("rolling_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createRollingCountForEvent("rolling_count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        final String batchDoc = "Collapser the batch size metric.";
        collector.addGauge(SUBSYSTEM, "batch_size_mean", batchDoc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getBatchSizeMean();
            }
        });

        createBatchSizePercentile("batch_size_percentile_25", 25, batchDoc);
        createBatchSizePercentile("batch_size_percentile_50", 50, batchDoc);
        createBatchSizePercentile("batch_size_percentile_75", 75, batchDoc);
        createBatchSizePercentile("batch_size_percentile_90", 90, batchDoc);
        createBatchSizePercentile("batch_size_percentile_99", 99, batchDoc);
        createBatchSizePercentile("batch_size_percentile_995", 99.5, batchDoc);

        final String shardDoc = "Collapser shard size metric.";
        collector.addGauge(SUBSYSTEM, "shard_size_mean", shardDoc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getShardSizeMean();
            }
        });

        createShardSizePercentile("shard_size_percentile_25", 25, shardDoc);
        createShardSizePercentile("shard_size_percentile_50", 50, shardDoc);
        createShardSizePercentile("shard_size_percentile_75", 75, shardDoc);
        createShardSizePercentile("shard_size_percentile_90", 90, shardDoc);
        createShardSizePercentile("shard_size_percentile_99", 99, shardDoc);
        createShardSizePercentile("shard_size_percentile_995", 99.5, shardDoc);

        if (exportProperties) {
            createIntegerProperty("property_value_rolling_statistical_window_in_milliseconds",
                    properties.metricsRollingStatisticalWindowInMilliseconds());
            createBooleanProperty("property_value_request_cache_enabled",
                    properties.requestCacheEnabled());
            createIntegerProperty("property_value_max_requests_in_batch",
                    properties.maxRequestsInBatch());
            createIntegerProperty("property_value_timer_delay_in_milliseconds",
                    properties.timerDelayInMilliseconds());
        }
    }

    private void createCumulativeCountForEvent(String metric, final HystrixRollingNumberEvent event) {
        String doc = "These are cumulative counts since the start of the application.";
        collector.addGauge(SUBSYSTEM, metric, doc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getCumulativeCount(event);
            }
        });
    }

    private void createRollingCountForEvent(String metric, final HystrixRollingNumberEvent event) {
        String doc = "These are \"point in time\" counts representing the last X seconds.";
        collector.addGauge(SUBSYSTEM, metric, doc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getRollingCount(event);
            }
        });
    }

    private void createBatchSizePercentile(String metric, final double percentile, String documentation) {
        collector.addGauge(SUBSYSTEM, metric, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getBatchSizePercentile(percentile);
            }
        });
    }

    private void createShardSizePercentile(String metric, final double percentile, String documentation) {
        collector.addGauge(SUBSYSTEM, metric, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getShardSizePercentile(percentile);
            }
        });
    }

    private void createIntegerProperty(String metric, final HystrixProperty<Integer> property) {
        String doc = "Configuration property partitioned by collapser_name.";
        collector.addGauge(SUBSYSTEM, metric, doc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return property.get();
            }
        });
    }

    private void createBooleanProperty(String metric, final HystrixProperty<Boolean> property) {
        String doc = "Configuration property partitioned by collapser_name.";
        collector.addGauge(SUBSYSTEM, metric, doc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return property.get() ? 1 : 0;
            }
        });
    }
}