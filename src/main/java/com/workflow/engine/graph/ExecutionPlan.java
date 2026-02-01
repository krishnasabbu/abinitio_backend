package com.workflow.engine.graph;

import java.util.List;
import java.util.Map;

public record ExecutionPlan(
    List<String> entryStepIds,
    Map<String, StepNode> steps,
    String workflowId
) {
    public ExecutionPlan(List<String> entryStepIds, Map<String, StepNode> steps) {
        this(entryStepIds, steps, null);
    }
}
