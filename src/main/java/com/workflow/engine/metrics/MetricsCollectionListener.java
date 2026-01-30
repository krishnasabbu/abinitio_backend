package com.workflow.engine.metrics;

import com.workflow.engine.model.MetricsConfig;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class MetricsCollectionListener implements StepExecutionListener {

    private final String nodeId;
    private final String nodeType;
    private final MetricsConfig metricsConfig;
    private final MetricsCollector metricsCollector;

    public MetricsCollectionListener(String nodeId, String nodeType, MetricsConfig metricsConfig, MetricsCollector metricsCollector) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.metricsConfig = metricsConfig;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        metricsCollector.collectMetrics(stepExecution, nodeId, nodeType, metricsConfig);
        return stepExecution.getExitStatus();
    }
}
