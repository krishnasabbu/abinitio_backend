package com.workflow.engine.config;

import com.workflow.engine.graph.ExecutionPlanValidator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "workflow.validation")
public class WorkflowValidationConfig {

    private boolean strictJoins = false;
    private boolean strictJoinUpstreams = false;
    private boolean requireExplicitJoin = false;

    public boolean isStrictJoins() {
        return strictJoins;
    }

    public void setStrictJoins(boolean strictJoins) {
        this.strictJoins = strictJoins;
    }

    public boolean isStrictJoinUpstreams() {
        return strictJoinUpstreams;
    }

    public void setStrictJoinUpstreams(boolean strictJoinUpstreams) {
        this.strictJoinUpstreams = strictJoinUpstreams;
    }

    public boolean isRequireExplicitJoin() {
        return requireExplicitJoin;
    }

    public void setRequireExplicitJoin(boolean requireExplicitJoin) {
        this.requireExplicitJoin = requireExplicitJoin;
    }

    @Bean
    public ExecutionPlanValidator.ValidationConfig validationConfig() {
        return ExecutionPlanValidator.ValidationConfig.fromProperties(
            strictJoins,
            strictJoinUpstreams,
            requireExplicitJoin
        );
    }
}
