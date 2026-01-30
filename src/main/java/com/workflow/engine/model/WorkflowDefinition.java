package com.workflow.engine.model;

import lombok.Data;
import java.util.List;

@Data
public class WorkflowDefinition {
    private String id;
    private String name;
    private List<NodeDefinition> nodes;
    private List<Edge> edges;
}
