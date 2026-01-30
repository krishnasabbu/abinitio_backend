package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor for workflow end node.
 *
 * Serves as the exit point for workflow execution. Collects all final items and
 * sets the workflow exit status. Marks the end of the workflow execution flow.
 *
 * Configuration:
 * - exitStatus: (optional) Exit status to set (default: "COMPLETED")
 *
 * Output:
 * Sets "outputItems" variable with all final items and "exitStatus" variable.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class EndExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(EndExecutor.class);

    @Override
    public String getNodeType() {
        return "End";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating end reader", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            logger.debug("nodeId={}, No input items", context.getNodeDefinition().getId());
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating end processor (identity)", context.getNodeDefinition().getId());
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating end writer", context.getNodeDefinition().getId());
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            String exitStatus = context.getNodeDefinition().getConfig() != null && context.getNodeDefinition().getConfig().has("exitStatus")
                ? context.getNodeDefinition().getConfig().get("exitStatus").asText()
                : "COMPLETED";
            context.setVariable("outputItems", outputList);
            context.setVariable("exitStatus", exitStatus);
            logger.info("nodeId={}, Workflow ending with status '{}' and {} final items",
                context.getNodeDefinition().getId(), exitStatus, outputList.size());
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating End node (no validation required)", context.getNodeDefinition().getId());
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return true;
    }
}
