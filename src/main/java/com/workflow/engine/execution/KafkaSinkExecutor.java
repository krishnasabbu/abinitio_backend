package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component
public class KafkaSinkExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSinkExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "KafkaSink";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String topic = config.has("topic") ? config.get("topic").asText() : "";

        if (topic.isEmpty()) {
            throw new IllegalArgumentException("nodeType=KafkaSink, nodeId=" + nodeId + ", missing topic");
        }

        String bootstrapServers = config.has("bootstrapServers") ? config.get("bootstrapServers").asText() : "localhost:9092";
        String keyField = config.has("keyField") ? config.get("keyField").asText() : null;
        String valueFormat = config.has("valueFormat") ? config.get("valueFormat").asText() : "json";
        String compressionType = config.has("compressionType") ? config.get("compressionType").asText() : "none";

        return chunk -> {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);

            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                for (Map<String, Object> item : chunk) {
                    String key = null;
                    if (keyField != null) {
                        Object keyValue = item.get(keyField);
                        if (keyValue != null) {
                            key = String.valueOf(keyValue);
                        }
                    }

                    String value;
                    if ("json".equals(valueFormat)) {
                        Map<String, Object> output = new HashMap<>(item);
                        output.keySet().removeIf(k -> k.startsWith("_"));
                        value = mapper.writeValueAsString(output);
                    } else {
                        value = String.valueOf(item.getOrDefault("value", item));
                    }

                    ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
                    producer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            logger.error("Failed to send message to Kafka: {}", exception.getMessage());
                        } else {
                            logger.debug("Sent message to topic {} partition {}", topic, metadata.partition());
                        }
                    });
                }
                producer.flush();
            } catch (Exception e) {
                logger.error("KafkaSink error: {}", e.getMessage());
                throw new RuntimeException("KafkaSink failed", e);
            }

            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : chunk) {
                outputItems.add(item);
            }
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("topic")) {
            throw new IllegalArgumentException("nodeType=KafkaSink, nodeId=" + context.getNodeDefinition().getId()
                + ", missing topic");
        }
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
