package com.workflow.engine.execution.routing;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutingStepListener implements StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(RoutingStepListener.class);

    private final EdgeBufferStore bufferStore;
    private final String executionId;
    private final String targetNodeId;
    private final String targetPort;

    public RoutingStepListener(EdgeBufferStore bufferStore, String executionId, String targetNodeId, String targetPort) {
        this.bufferStore = bufferStore;
        this.executionId = executionId;
        this.targetNodeId = targetNodeId;
        this.targetPort = targetPort != null ? targetPort : "in";
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        logger.debug("RoutingStepListener: beforeStep for {} with port {}", targetNodeId, targetPort);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        logger.debug("RoutingStepListener: afterStep for {} with port {}", targetNodeId, targetPort);
        return stepExecution.getExitStatus();
    }
}
