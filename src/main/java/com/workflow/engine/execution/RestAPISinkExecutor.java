package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executor for writing data to REST API endpoints.
 *
 * Supports multiple HTTP methods (POST, PUT, PATCH, DELETE), batch processing,
 * template-based request body generation with field interpolation, various authentication
 * schemes, and configurable error handling (stop on error or skip failed items).
 *
 * Configuration properties:
 * - url: (required) The REST API endpoint URL
 * - method: HTTP method (default: POST)
 * - authType: Authentication type - "API Key", "Bearer Token", "Basic Auth"
 * - authKey: Auth credential (for API Key and Bearer Token)
 * - authUser/authPass: Credentials for Basic Auth
 * - headers: Custom HTTP headers as JSON object
 * - batchSize: Number of items per API call (default: 1)
 * - bodyTemplate: Request body template with {{fieldName}} placeholders
 * - onFailure: Error handling - "Stop" or "Skip" (default: Skip)
 *
 * Thread safety: Thread-safe. Creates new writer instances per execution.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class RestAPISinkExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(RestAPISinkExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getNodeType() {
        return "RestAPISink";
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
        logger.debug("Creating REST API sink writer");
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String url = config.has("url") ? config.get("url").asText() : "";

        if (url.isEmpty()) {
            logger.error("nodeId={}, REST API Sink URL is missing", nodeId);
            throw new IllegalArgumentException("nodeType=RestAPISink, nodeId=" + nodeId + ", missing url");
        }

        int batchSize = config.has("batchSize") ? config.get("batchSize").asInt() : 1;
        String method = config.has("method") ? config.get("method").asText() : "POST";
        String onFailure = config.has("onFailure") ? config.get("onFailure").asText() : "Skip";

        logger.debug("nodeId={}, REST API sink configured: url={}, method={}, batchSize={}, onFailure={}",
            nodeId, url, method, batchSize, onFailure);

        return chunk -> {
            List<Map<String, Object>> items = chunk.getItems();
            logger.info("nodeId={}, Writing {} items to REST API", nodeId, items.size());

            if (batchSize > 1) {
                writeBatchedItems(items, url, config, method, nodeId, onFailure);
            } else {
                writeSingleItems(items, url, config, method, nodeId, onFailure);
            }

            context.setVariable("outputItems", new ArrayList<>(items));
        };
    }

    /**
     * Writes items as individual API calls.
     *
     * @param items Items to write
     * @param url API endpoint URL
     * @param config Node configuration
     * @param method HTTP method
     * @param nodeId Node identifier for logging
     * @param onFailure Error handling mode (Stop or Skip)
     */
    private void writeSingleItems(List<Map<String, Object>> items, String url, JsonNode config,
                                  String method, String nodeId, String onFailure) {
        int successCount = 0;
        int failureCount = 0;

        for (Map<String, Object> item : items) {
            try {
                sendItem(item, url, config, method, nodeId);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                logger.warn("nodeId={}, REST API call failed for item (onFailure={}): {}",
                    nodeId, onFailure, e.getMessage());

                if ("Stop".equals(onFailure)) {
                    logger.error("nodeId={}, Stopping execution due to API call failure", nodeId);
                    throw new RuntimeException("REST API call failed: " + e.getMessage(), e);
                }
            }
        }

        logger.info("nodeId={}, Single item writes completed: {} success, {} failed", nodeId, successCount, failureCount);
    }

    /**
     * Writes items in batches to the API endpoint.
     *
     * @param items Items to write
     * @param url API endpoint URL
     * @param config Node configuration
     * @param method HTTP method
     * @param nodeId Node identifier for logging
     * @param onFailure Error handling mode (Stop or Skip)
     */
    private void writeBatchedItems(List<Map<String, Object>> items, String url, JsonNode config,
                                   String method, String nodeId, String onFailure) {
        int batchSize = config.has("batchSize") ? config.get("batchSize").asInt() : 1;
        List<Map<String, Object>> batch = new ArrayList<>();
        int batchCount = 0;
        int successCount = 0;
        int failureCount = 0;

        for (Map<String, Object> item : items) {
            batch.add(item);
            if (batch.size() >= batchSize) {
                batchCount++;
                try {
                    sendBatch(batch, url, config, method, nodeId);
                    successCount += batch.size();
                    logger.debug("nodeId={}, Batch {} sent successfully", nodeId, batchCount);
                } catch (Exception e) {
                    failureCount += batch.size();
                    logger.warn("nodeId={}, Batch {} failed (onFailure={}): {}",
                        nodeId, batchCount, onFailure, e.getMessage());

                    if ("Stop".equals(onFailure)) {
                        logger.error("nodeId={}, Stopping execution due to batch API call failure", nodeId);
                        throw new RuntimeException("REST API batch call failed: " + e.getMessage(), e);
                    }
                }
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            batchCount++;
            try {
                sendBatch(batch, url, config, method, nodeId);
                successCount += batch.size();
                logger.debug("nodeId={}, Final batch {} sent successfully", nodeId, batchCount);
            } catch (Exception e) {
                failureCount += batch.size();
                logger.warn("nodeId={}, Final batch {} failed (onFailure={}): {}",
                    nodeId, batchCount, onFailure, e.getMessage());

                if ("Stop".equals(onFailure)) {
                    logger.error("nodeId={}, Stopping execution due to final batch API call failure", nodeId);
                    throw new RuntimeException("REST API batch call failed: " + e.getMessage(), e);
                }
            }
        }

        logger.info("nodeId={}, Batch writes completed: {} batches, {} items success, {} failed",
            nodeId, batchCount, successCount, failureCount);
    }

    /**
     * Sends a single item to the REST API endpoint.
     *
     * @param item The item to send
     * @param url API endpoint URL
     * @param config Node configuration
     * @param method HTTP method
     * @param nodeId Node identifier for logging
     * @throws Exception if the API call fails
     */
    private void sendItem(Map<String, Object> item, String url, JsonNode config, String method, String nodeId) throws Exception {
        try {
            String body = buildRequestBody(item, config, nodeId);
            HttpHeaders headers = buildHeaders(config);
            addAuthHeaders(headers, config, nodeId);

            logger.debug("nodeId={}, Sending {} request to {}", nodeId, method, url);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            var response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);

            logger.debug("nodeId={}, Item sent successfully, response status: {}", nodeId, response.getStatusCode());
        } catch (Exception e) {
            logger.error("nodeId={}, Failed to send item to {}: {}", nodeId, url, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Sends a batch of items to the REST API endpoint.
     *
     * @param batch The batch of items to send
     * @param url API endpoint URL
     * @param config Node configuration
     * @param method HTTP method
     * @param nodeId Node identifier for logging
     * @throws Exception if the API call fails
     */
    private void sendBatch(List<Map<String, Object>> batch, String url, JsonNode config, String method, String nodeId) throws Exception {
        try {
            String body = mapper.writeValueAsString(batch);
            HttpHeaders headers = buildHeaders(config);
            addAuthHeaders(headers, config, nodeId);

            logger.debug("nodeId={}, Sending {} batch of {} items to {}", nodeId, method, batch.size(), url);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            var response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);

            logger.debug("nodeId={}, Batch sent successfully, response status: {}", nodeId, response.getStatusCode());
        } catch (Exception e) {
            logger.error("nodeId={}, Failed to send batch of {} items to {}: {}",
                nodeId, batch.size(), url, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Builds the request body, either using the configured template with field interpolation
     * or serializing the item as JSON.
     *
     * @param item The item to serialize
     * @param config Node configuration
     * @param nodeId Node identifier for logging
     * @return The request body as a string
     * @throws Exception if serialization fails
     */
    private String buildRequestBody(Map<String, Object> item, JsonNode config, String nodeId) throws Exception {
        if (!config.has("bodyTemplate") || config.get("bodyTemplate").asText().isEmpty()) {
            logger.debug("nodeId={}, Using default JSON serialization for request body", nodeId);
            return mapper.writeValueAsString(item);
        }

        String template = config.get("bodyTemplate").asText();
        logger.debug("nodeId={}, Using body template for request", nodeId);

        String result = template;
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(template);

        int substitutionCount = 0;
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            Object value = item.get(fieldName);
            String stringValue = value != null ? String.valueOf(value) : "";
            result = result.replace("{{" + fieldName + "}}", stringValue);
            substitutionCount++;
            logger.debug("nodeId={}, Template substitution {}: {} = {}", nodeId, substitutionCount, fieldName, "***");
        }

        logger.debug("nodeId={}, Template interpolation complete with {} substitutions", nodeId, substitutionCount);
        return result;
    }

    /**
     * Builds HTTP headers from the configuration, always including Content-Type.
     *
     * @param config Node configuration
     * @return HTTP headers with configured custom headers
     */
    private HttpHeaders buildHeaders(JsonNode config) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        if (config.has("headers")) {
            JsonNode headersNode = config.get("headers");
            headersNode.fields().forEachRemaining(entry -> {
                headers.add(entry.getKey(), entry.getValue().asText());
                logger.debug("Added custom header: {}", entry.getKey());
            });
        }

        return headers;
    }

    /**
     * Adds authentication headers based on the configured authentication type.
     *
     * @param headers HTTP headers to modify
     * @param config Node configuration
     * @param nodeId Node identifier for logging
     */
    private void addAuthHeaders(HttpHeaders headers, JsonNode config, String nodeId) {
        if (!config.has("authType")) {
            logger.debug("nodeId={}, No authentication configured", nodeId);
            return;
        }

        String authType = config.get("authType").asText();
        logger.debug("nodeId={}, Adding authentication headers: {}", nodeId, authType);

        try {
            switch (authType) {
                case "API Key":
                    if (config.has("authKey")) {
                        headers.add("X-API-Key", config.get("authKey").asText());
                        logger.debug("nodeId={}, Added X-API-Key header", nodeId);
                    } else {
                        logger.warn("nodeId={}, API Key auth type selected but authKey is missing", nodeId);
                    }
                    break;
                case "Bearer Token":
                    if (config.has("authKey")) {
                        headers.add("Authorization", "Bearer " + config.get("authKey").asText());
                        logger.debug("nodeId={}, Added Bearer Token header", nodeId);
                    } else {
                        logger.warn("nodeId={}, Bearer Token auth type selected but authKey is missing", nodeId);
                    }
                    break;
                case "Basic Auth":
                    if (config.has("authUser") && config.has("authPass")) {
                        String credentials = config.get("authUser").asText() + ":" + config.get("authPass").asText();
                        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                        headers.add("Authorization", "Basic " + encoded);
                        logger.debug("nodeId={}, Added Basic Auth header", nodeId);
                    } else {
                        logger.warn("nodeId={}, Basic Auth selected but credentials are missing", nodeId);
                    }
                    break;
                default:
                    logger.warn("nodeId={}, Unknown authentication type: {}", nodeId, authType);
            }
        } catch (Exception e) {
            logger.error("nodeId={}, Failed to add authentication headers: {}", nodeId, e.getMessage(), e);
        }
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("Validating REST API Sink configuration");
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();

        if (config == null) {
            logger.error("nodeId={}, REST API Sink configuration is null", nodeId);
            throw new IllegalArgumentException("nodeType=RestAPISink, nodeId=" + nodeId + ", config is null");
        }

        if (!config.has("url")) {
            logger.error("nodeId={}, REST API Sink URL is missing", nodeId);
            throw new IllegalArgumentException("nodeType=RestAPISink, nodeId=" + nodeId + ", missing url");
        }

        String url = config.get("url").asText();
        int batchSize = config.has("batchSize") ? config.get("batchSize").asInt() : 1;

        if (batchSize < 1) {
            logger.error("nodeId={}, Invalid batchSize: {}", nodeId, batchSize);
            throw new IllegalArgumentException("nodeType=RestAPISink, nodeId=" + nodeId + ", batchSize must be >= 1");
        }

        logger.debug("nodeId={}, REST API Sink configuration valid: url={}, batchSize={}", nodeId, url, batchSize);
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
