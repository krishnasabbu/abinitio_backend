package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;

public interface NodeExecutor<I, O> {

    ItemReader<I> createReader(NodeExecutionContext context);

    ItemProcessor<I, O> createProcessor(NodeExecutionContext context);

    ItemWriter<O> createWriter(NodeExecutionContext context);

    void validate(NodeExecutionContext context);

    boolean supportsMetrics();

    boolean supportsFailureHandling();

    String getNodeType();
}
