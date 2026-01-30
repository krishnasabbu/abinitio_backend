package com.workflow.engine.execution;

import com.workflow.engine.model.NodeDefinition;
import lombok.Data;
import org.springframework.batch.core.StepExecution;

import java.util.HashMap;
import java.util.Map;

@Data
public class NodeExecutionContext {
    private NodeDefinition nodeDefinition;
    private StepExecution stepExecution;
    private Map<String, Object> variables = new HashMap<>();
    private Map<String, Object> inputData = new HashMap<>();
    private Map<String, Object> outputData = new HashMap<>();

    public NodeExecutionContext(NodeDefinition nodeDefinition, StepExecution stepExecution) {
        this.nodeDefinition = nodeDefinition;
        this.stepExecution = stepExecution;
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void setInput(String key, Object value) {
        inputData.put(key, value);
    }

    public Object getInput(String key) {
        return inputData.get(key);
    }

    public void setOutput(String key, Object value) {
        outputData.put(key, value);
    }

    public Object getOutput(String key) {
        return outputData.get(key);
    }
}
