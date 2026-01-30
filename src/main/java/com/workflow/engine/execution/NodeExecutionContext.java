package com.workflow.engine.execution;

import com.workflow.engine.model.NodeDefinition;
import lombok.Data;
import org.springframework.batch.core.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution context passed to node executors during workflow execution.
 *
 * Contains the node definition, Spring Batch step execution context, and
 * variable/data maps for inter-node communication. Used by executors to:
 * - Access node configuration and definition
 * - Share state variables with other nodes
 * - Manage input/output data for the node
 * - Access Spring Batch execution metadata
 *
 * Thread safety: Not thread-safe. Each node execution gets its own context instance.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see NodeExecutor
 * @see NodeDefinition
 */
@Data
public class NodeExecutionContext {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutionContext.class);

    /** The definition of the node being executed */
    private NodeDefinition nodeDefinition;

    /** Spring Batch StepExecution metadata for this node execution */
    private StepExecution stepExecution;

    /** Map for sharing variables and state between nodes */
    private Map<String, Object> variables = new HashMap<>();

    /** Map of input data/items for this node */
    private Map<String, Object> inputData = new HashMap<>();

    /** Map of output data/items produced by this node */
    private Map<String, Object> outputData = new HashMap<>();

    /**
     * Constructs an execution context for a node.
     *
     * @param nodeDefinition the definition of the node being executed
     * @param stepExecution the Spring Batch step execution metadata
     */
    public NodeExecutionContext(NodeDefinition nodeDefinition, StepExecution stepExecution) {
        this.nodeDefinition = nodeDefinition;
        this.stepExecution = stepExecution;
        logger.debug("Created execution context for node: {}", nodeDefinition.getId());
    }

    /**
     * Sets a variable in the shared execution context.
     *
     * Variables set here are accessible to downstream nodes in the workflow.
     * Used for inter-node communication and state passing.
     *
     * @param key the variable name
     * @param value the variable value
     */
    public void setVariable(String key, Object value) {
        logger.debug("Setting variable: {} = {}", key, value != null ? value.getClass().getSimpleName() : "null");
        variables.put(key, value);
    }

    /**
     * Retrieves a variable from the shared execution context.
     *
     * @param key the variable name
     * @return the variable value, or null if not set
     */
    public Object getVariable(String key) {
        Object value = variables.get(key);
        logger.debug("Getting variable: {} = {}", key, value != null ? value.getClass().getSimpleName() : "null");
        return value;
    }

    /**
     * Sets input data for this node.
     *
     * @param key the input data key
     * @param value the input data value
     */
    public void setInput(String key, Object value) {
        logger.debug("Setting input: {} = {}", key, value != null ? value.getClass().getSimpleName() : "null");
        inputData.put(key, value);
    }

    /**
     * Retrieves input data for this node.
     *
     * @param key the input data key
     * @return the input data value, or null if not set
     */
    public Object getInput(String key) {
        Object value = inputData.get(key);
        logger.debug("Getting input: {} = {}", key, value != null ? value.getClass().getSimpleName() : "null");
        return value;
    }

    /**
     * Sets output data produced by this node.
     *
     * @param key the output data key
     * @param value the output data value
     */
    public void setOutput(String key, Object value) {
        logger.debug("Setting output: {} = {}", key, value != null ? value.getClass().getSimpleName() : "null");
        outputData.put(key, value);
    }

    /**
     * Retrieves output data produced by this node.
     *
     * @param key the output data key
     * @return the output data value, or null if not set
     */
    public Object getOutput(String key) {
        Object value = outputData.get(key);
        logger.debug("Getting output: {} = {}", key, value != null ? value.getClass().getSimpleName() : "null");
        return value;
    }
}
