package com.workflow.engine.failure;

import com.workflow.engine.model.FailureAction;
import com.workflow.engine.model.FailurePolicy;
import org.springframework.batch.core.StepExecution;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.stereotype.Component;

@Component
public class FailureHandler {

    public RetryPolicy createRetryPolicy(FailurePolicy failurePolicy) {
        if (failurePolicy == null || failurePolicy.getAction() != FailureAction.RETRY) {
            return new SimpleRetryPolicy(0);
        }

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(failurePolicy.getMaxRetries() != null ? failurePolicy.getMaxRetries() : 3);
        return retryPolicy;
    }

    public boolean shouldSkip(FailurePolicy failurePolicy, Throwable exception) {
        if (failurePolicy == null) {
            return false;
        }

        return failurePolicy.getAction() == FailureAction.SKIP || failurePolicy.isSkipOnError();
    }

    public boolean shouldStop(FailurePolicy failurePolicy) {
        if (failurePolicy == null) {
            return true;
        }

        return failurePolicy.getAction() == FailureAction.STOP;
    }

    public String getRouteTarget(FailurePolicy failurePolicy) {
        if (failurePolicy == null || failurePolicy.getAction() != FailureAction.ROUTE) {
            return null;
        }

        return failurePolicy.getRouteToNode();
    }

    public void handleFailure(StepExecution stepExecution, Throwable exception, FailurePolicy failurePolicy) {
        if (failurePolicy == null) {
            return;
        }

        stepExecution.getExecutionContext().put("lastError", exception.getMessage());
        stepExecution.getExecutionContext().put("failureAction", failurePolicy.getAction().name());

        switch (failurePolicy.getAction()) {
            case SKIP:
                stepExecution.getExecutionContext().put("skipped", true);
                break;
            case RETRY:
                stepExecution.getExecutionContext().put("retrying", true);
                break;
            case ROUTE:
                stepExecution.getExecutionContext().put("routeTarget", failurePolicy.getRouteToNode());
                break;
            case STOP:
            default:
                stepExecution.getExecutionContext().put("stopped", true);
                break;
        }
    }
}
