package com.workflow.engine.metrics;

import com.workflow.engine.model.MetricsConfig;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricsCollector {

    private final Map<String, NodeMetrics> metricsMap = new ConcurrentHashMap<>();

    public NodeMetrics collectMetrics(StepExecution stepExecution, String nodeId, String nodeType, MetricsConfig config) {
        if (config == null || !config.isEnabled()) {
            return null;
        }

        NodeMetrics metrics = new NodeMetrics();
        metrics.setNodeId(nodeId);
        metrics.setNodeType(nodeType);

        if (config.isTrackExecutionTime()) {
            long executionTime = 0;
            if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
                long startTimeMs = stepExecution.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                long endTimeMs = stepExecution.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                executionTime = endTimeMs - startTimeMs;
                metrics.setStartTime(startTimeMs);
                metrics.setEndTime(endTimeMs);
            }
            metrics.setExecutionTimeMs(executionTime);
        }

        if (config.isTrackReadCount()) {
            metrics.setReadCount(stepExecution.getReadCount());
        }

        if (config.isTrackWriteCount()) {
            metrics.setWriteCount(stepExecution.getWriteCount());
        }

        if (config.isTrackErrorCount()) {
            metrics.setErrorCount(stepExecution.getReadSkipCount() + stepExecution.getProcessSkipCount() + stepExecution.getWriteSkipCount());
            metrics.setSkipCount(stepExecution.getSkipCount());
        }

        metrics.setStatus(stepExecution.getExitStatus().getExitCode());

        metricsMap.put(nodeId, metrics);
        return metrics;
    }

    public NodeMetrics getMetrics(String nodeId) {
        return metricsMap.get(nodeId);
    }

    public List<NodeMetrics> getAllMetrics() {
        return new ArrayList<>(metricsMap.values());
    }

    public void clearMetrics() {
        metricsMap.clear();
    }
}
