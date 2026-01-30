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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for workflow start node.
 *
 * Serves as the entry point for workflow execution. Generates a single empty record
 * that flows through the rest of the workflow. Typically used in conjunction with
 * source nodes to load the initial data.
 *
 * Configuration:
 * - None required. Start node configuration is ignored.
 *
 * Output:
 * Sets "outputItems" variable with a single empty map record.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class StartExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(StartExecutor.class);

    @Override
    public String getNodeType() {
        return "Start";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating start reader (single empty item)", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>());
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating start processor (identity)", context.getNodeDefinition().getId());
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating start writer", context.getNodeDefinition().getId());
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>(items);
            logger.info("nodeId={}, Workflow started with {} items", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating Start node (no validation required)", context.getNodeDefinition().getId());
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
