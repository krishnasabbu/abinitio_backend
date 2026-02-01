package com.workflow.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "workflow.error")
public class WorkflowErrorPolicyConfig {

    public enum ErrorPolicy {
        FAIL,
        STOP,
        COMPENSATE_AND_FAIL,
        COMPENSATE_AND_COMPLETE
    }

    private ErrorPolicy policy = ErrorPolicy.FAIL;

    public ErrorPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ErrorPolicy policy) {
        this.policy = policy;
    }

    public boolean shouldMarkJobFailed() {
        return policy == ErrorPolicy.FAIL || policy == ErrorPolicy.COMPENSATE_AND_FAIL;
    }

    public boolean shouldRunCompensation() {
        return policy == ErrorPolicy.COMPENSATE_AND_FAIL || policy == ErrorPolicy.COMPENSATE_AND_COMPLETE;
    }
}
