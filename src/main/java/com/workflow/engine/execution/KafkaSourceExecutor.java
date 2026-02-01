package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

@Component
public class KafkaSourceExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSourceExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "KafkaSource";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();

        String topics = config.has("topics") ? config.get("topics").asText() : "";
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("nodeType=KafkaSource, nodeId=" + nodeId + ", missing topics");
        }

        String bootstrapServers = config.has("bootstrapServers") ? config.get("bootstrapServers").asText() : "localhost:9092";
        String autoOffsetReset = config.has("autoOffsetReset") ? config.get("autoOffsetReset").asText() : "earliest";
        long maxMessages = config.has("maxMessages") ? config.get("maxMessages").asLong() : 100;
        long timeoutMs = config.has("timeoutMs") ? config.get("timeoutMs").asLong() : 10000;
        String valueFormat = config.has("valueFormat") ? config.get("valueFormat").asText() : "json";

        return new KafkaItemReader(bootstrapServers, topics.split(","), autoOffsetReset, valueFormat, maxMessages, timeoutMs);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> context.setVariable("outputItems", new ArrayList<>(items.getItems()));
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("topics")) {
            throw new IllegalArgumentException("nodeType=KafkaSource, nodeId=" + context.getNodeDefinition().getId()
                + ", missing topics");
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

    private static class KafkaItemReader implements ItemReader<Map<String, Object>> {

        private final KafkaConsumer<String, String> consumer;
        private final String valueFormat;
        private final long maxMessages;
        private final long startMs;
        private final long timeoutMs;
        private Iterator<ConsumerRecord<String, String>> currentIterator;
        private long messagesRead = 0;

        public KafkaItemReader(String bootstrapServers, String[] topics, String autoOffsetReset,
                             String valueFormat, long maxMessages, long timeoutMs) {
            this.valueFormat = valueFormat;
            this.maxMessages = maxMessages;
            this.timeoutMs = timeoutMs;
            this.startMs = System.currentTimeMillis();

            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "bolt-consumer-" + UUID.randomUUID());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

            this.consumer = new KafkaConsumer<>(props);
            this.consumer.subscribe(Arrays.asList(topics));
            this.currentIterator = Collections.emptyIterator();

            logger.info("KafkaSource initialized for topics: {}", String.join(",", topics));
        }

        @Override
        public Map<String, Object> read() {
            if (messagesRead >= maxMessages) {
                return null;
            }

            if (System.currentTimeMillis() - startMs > timeoutMs) {
                logger.info("KafkaSource timeout reached");
                return null;
            }

            while (true) {
                if (currentIterator.hasNext()) {
                    ConsumerRecord<String, String> record = currentIterator.next();
                    messagesRead++;
                    return parseRecord(record);
                }

                var records = consumer.poll(Duration.ofMillis(1000));
                if (records.isEmpty()) {
                    if (messagesRead == 0 && System.currentTimeMillis() - startMs > timeoutMs) {
                        return null;
                    }
                    continue;
                }

                currentIterator = records.iterator();
            }
        }

        private Map<String, Object> parseRecord(ConsumerRecord<String, String> record) {
            Map<String, Object> item = new HashMap<>();

            try {
                if ("json".equals(valueFormat)) {
                    Object parsed = mapper.readValue(record.value(), Object.class);
                    if (parsed instanceof Map) {
                        item.putAll((Map<String, Object>) parsed);
                    } else {
                        item.put("value", parsed);
                    }
                } else {
                    item.put("value", record.value());
                }
            } catch (Exception e) {
                logger.warn("Failed to parse Kafka message, treating as string: {}", e.getMessage());
                item.put("value", record.value());
            }

            item.put("_kafkaTopic", record.topic());
            item.put("_kafkaPartition", record.partition());
            item.put("_kafkaOffset", record.offset());
            item.put("_kafkaTimestamp", record.timestamp());
            if (record.key() != null) {
                item.put("_kafkaKey", record.key());
            }

            return item;
        }
    }
}
