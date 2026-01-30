package com.workflow.engine.execution.routing;

import java.util.Objects;

public record OutputPort(
    String targetNodeId,
    String sourcePort,
    String targetPort,
    boolean isControl
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputPort that)) return false;
        return Objects.equals(targetNodeId, that.targetNodeId) &&
               Objects.equals(sourcePort, that.sourcePort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetNodeId, sourcePort);
    }
}
