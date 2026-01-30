package com.workflow.engine.execution.routing;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutingJobListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(RoutingJobListener.class);

    private final EdgeBufferStore bufferStore;
    private final String executionId;

    public RoutingJobListener(EdgeBufferStore bufferStore, String executionId) {
        this.bufferStore = bufferStore;
        this.executionId = executionId;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.debug("RoutingJobListener: beforeJob for execution {}", executionId);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        logger.debug("RoutingJobListener: afterJob for execution {}, cleaning up buffers", executionId);
        bufferStore.clearExecution(executionId);
    }
}
