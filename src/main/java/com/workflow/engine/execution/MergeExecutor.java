package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MergeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Merge";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items1 = (List<Map<String, Object>>) context.getVariable("in1InputItems");
        List<Map<String, Object>> items2 = (List<Map<String, Object>>) context.getVariable("in2InputItems");

        if (items1 == null) {
            items1 = new ArrayList<>();
        }
        if (items2 == null) {
            items2 = new ArrayList<>();
        }

        List<Map<String, Object>> combined = new ArrayList<>(items1);
        combined.addAll(items2);

        return new ListItemReader<>(combined);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputItems.add(item);
                }
            }
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
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
