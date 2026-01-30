package com.workflow.engine.failure;

import com.workflow.engine.model.FailurePolicy;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class FailureHandlingListener implements StepExecutionListener {

    private final FailurePolicy failurePolicy;
    private final FailureHandler failureHandler;

    public FailureHandlingListener(FailurePolicy failurePolicy, FailureHandler failureHandler) {
        this.failurePolicy = failurePolicy;
        this.failureHandler = failureHandler;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.FAILED.getExitCode())) {
            Exception exception = null;
            for (Throwable t : stepExecution.getFailureExceptions()) {
                if (t instanceof Exception) {
                    exception = (Exception) t;
                    break;
                }
            }

            if (exception != null) {
                failureHandler.handleFailure(stepExecution, exception, failurePolicy);
            }
        }

        return stepExecution.getExitStatus();
    }
}
