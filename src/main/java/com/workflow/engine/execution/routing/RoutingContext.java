package com.workflow.engine.execution.routing;

import java.util.List;
import java.util.Map;

public class RoutingContext {

    private final String executionId;
    private final String sourceNodeId;
    private final List<OutputPort> outputPorts;
    private final EdgeBufferStore bufferStore;

    public RoutingContext(String executionId, String sourceNodeId, List<OutputPort> outputPorts, EdgeBufferStore bufferStore) {
        this.executionId = executionId;
        this.sourceNodeId = sourceNodeId;
        this.outputPorts = outputPorts != null ? outputPorts : List.of();
        this.bufferStore = bufferStore;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public List<OutputPort> getOutputPorts() {
        return outputPorts;
    }

    public EdgeBufferStore getBufferStore() {
        return bufferStore;
    }

    public void routeRecord(Map<String, Object> record, String routeKey) {
        if (routeKey == null) {
            routeToDefault(record);
            return;
        }

        for (OutputPort port : outputPorts) {
            if (routeKey.equals(port.sourcePort())) {
                bufferStore.addRecord(executionId, port.targetNodeId(), port.targetPort(), record);
                return;
            }
        }

        routeToDefault(record);
    }

    public void routeToDefault(Map<String, Object> record) {
        if (outputPorts.isEmpty()) {
            return;
        }

        OutputPort defaultPort = outputPorts.get(0);
        bufferStore.addRecord(executionId, defaultPort.targetNodeId(), defaultPort.targetPort(), record);
    }

    public void routeToAllPorts(Map<String, Object> record) {
        for (OutputPort port : outputPorts) {
            bufferStore.addRecord(executionId, port.targetNodeId(), port.targetPort(), record);
        }
    }
}
