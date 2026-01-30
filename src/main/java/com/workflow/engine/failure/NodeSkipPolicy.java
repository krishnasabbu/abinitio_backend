package com.workflow.engine.failure;

import com.workflow.engine.model.FailurePolicy;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;

public class NodeSkipPolicy implements SkipPolicy {

    private final FailurePolicy failurePolicy;

    public NodeSkipPolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (failurePolicy == null) {
            return false;
        }

        return failurePolicy.isSkipOnError();
    }
}
