package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component
public class RestAPISourceExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(RestAPISourceExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getNodeType() {
        return "RestAPISource";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String url = config.has("url") ? config.get("url").asText() : "";

        if (url.isEmpty()) {
            throw new IllegalArgumentException("nodeType=RestAPISource, nodeId=" + nodeId + ", missing url");
        }

        return new RestAPIItemReader(url, config, restTemplate, mapper);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> context.setVariable("outputItems", new ArrayList<>(items));
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("url")) {
            throw new IllegalArgumentException("nodeType=RestAPISource, nodeId=" + context.getNodeDefinition().getId()
                + ", missing url");
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

    private static class RestAPIItemReader implements ItemReader<Map<String, Object>> {

        private final String url;
        private final JsonNode config;
        private final RestTemplate restTemplate;
        private final ObjectMapper mapper;
        private Iterator<Map<String, Object>> itemIterator;
        private boolean initialized = false;

        public RestAPIItemReader(String url, JsonNode config, RestTemplate restTemplate, ObjectMapper mapper) {
            this.url = url;
            this.config = config;
            this.restTemplate = restTemplate;
            this.mapper = mapper;
        }

        @Override
        public Map<String, Object> read() {
            if (!initialized) {
                initialize();
                initialized = true;
            }

            if (itemIterator != null && itemIterator.hasNext()) {
                return itemIterator.next();
            }
            return null;
        }

        private void initialize() {
            try {
                List<Map<String, Object>> items = fetchFromAPI();
                itemIterator = items.iterator();
            } catch (Exception e) {
                logger.error("Failed to fetch from REST API: {}", e.getMessage());
                itemIterator = Collections.emptyIterator();
            }
        }

        private List<Map<String, Object>> fetchFromAPI() throws Exception {
            String method = config.has("method") ? config.get("method").asText() : "GET";
            String body = config.has("requestBody") ? config.get("requestBody").asText() : null;
            int timeout = config.has("timeout") ? config.get("timeout").asInt() : 30;

            HttpHeaders headers = new HttpHeaders();
            if (config.has("headers")) {
                JsonNode headersNode = config.get("headers");
                headersNode.fields().forEachRemaining(entry ->
                    headers.add(entry.getKey(), entry.getValue().asText())
                );
            }

            String finalUrl = url;
            if (config.has("queryParams")) {
                StringBuilder queryString = new StringBuilder("?");
                JsonNode params = config.get("queryParams");
                params.fields().forEachRemaining(entry ->
                    queryString.append(entry.getKey()).append("=").append(entry.getValue().asText()).append("&")
                );
                finalUrl = url + queryString.toString();
            }

            addAuthHeaders(headers, config);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(finalUrl, HttpMethod.valueOf(method), entity, String.class);

            String jsonPath = config.has("jsonPath") ? config.get("jsonPath").asText() : "";
            return parseResponse(response.getBody(), jsonPath);
        }

        private void addAuthHeaders(HttpHeaders headers, JsonNode config) {
            if (!config.has("authType")) {
                return;
            }

            String authType = config.get("authType").asText();
            switch (authType) {
                case "API Key":
                    if (config.has("authKey")) {
                        headers.add("X-API-Key", config.get("authKey").asText());
                    }
                    break;
                case "Bearer Token":
                    if (config.has("authKey")) {
                        headers.add("Authorization", "Bearer " + config.get("authKey").asText());
                    }
                    break;
                case "Basic Auth":
                    if (config.has("authUser") && config.has("authPass")) {
                        String credentials = config.get("authUser").asText() + ":" + config.get("authPass").asText();
                        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                        headers.add("Authorization", "Basic " + encoded);
                    }
                    break;
            }
        }

        private List<Map<String, Object>> parseResponse(String body, String jsonPath) throws Exception {
            List<Map<String, Object>> results = new ArrayList<>();
            JsonNode root = mapper.readTree(body);

            JsonNode dataNode = root;
            if (!jsonPath.isEmpty()) {
                String[] parts = jsonPath.split("\\.");
                for (String part : parts) {
                    dataNode = dataNode.get(part);
                    if (dataNode == null) {
                        return results;
                    }
                }
            }

            if (dataNode.isArray()) {
                dataNode.forEach(item -> {
                    try {
                        Map<String, Object> map = mapper.convertValue(item, Map.class);
                        results.add(map);
                    } catch (Exception e) {
                        logger.warn("Failed to convert array item: {}", e.getMessage());
                    }
                });
            } else if (dataNode.isObject()) {
                Map<String, Object> map = mapper.convertValue(dataNode, Map.class);
                results.add(map);
            }

            return results;
        }
    }
}
