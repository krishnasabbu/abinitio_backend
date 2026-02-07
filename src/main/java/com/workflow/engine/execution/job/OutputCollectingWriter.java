package com.workflow.engine.execution.job;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OutputCollectingWriter<T> implements ItemWriter<T> {

    private final ItemWriter<T> delegate;
    private final List<Map<String, Object>> collectedItems = new ArrayList<>();

    public OutputCollectingWriter(ItemWriter<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(Chunk<? extends T> items) throws Exception {
        delegate.write(items);

        for (T item : items) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) item;
                collectedItems.add(record);
            }
        }
    }

    public List<Map<String, Object>> getCollectedItems() {
        return Collections.unmodifiableList(collectedItems);
    }
}
