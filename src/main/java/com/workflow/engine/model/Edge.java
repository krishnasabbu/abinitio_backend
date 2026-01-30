package com.workflow.engine.model;

import lombok.Data;

@Data
public class Edge {
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;
    private boolean isControl;
}
