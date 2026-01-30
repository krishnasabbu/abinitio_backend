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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Executor for reading data from REST APIs.
 *
 * Supports multiple HTTP methods (GET, POST, PUT, DELETE), various authentication schemes
 * (API Key, Bearer Token, Basic Auth), custom headers, query parameters, and JSON path
 * navigation for extracting data from nested response structures.
 *
 * Configuration properties:
 * - url: (required) The REST API endpoint URL
 * - method: HTTP method (default: GET)
 * - authType: Authentication type - "API Key", "Bearer Token", "Basic Auth"
 * - authKey: Auth credential (for API Key and Bearer Token)
 * - authUser/authPass: Credentials for Basic Auth
 * - headers: Custom HTTP headers as JSON object
 * - queryParams: URL query parameters as JSON object
 * - requestBody: Request body for POST/PUT methods
 * - jsonPath: Dot-separated path to data in response (e.g., "data.items")
 * - timeout: Read timeout in seconds (default: 30)
 *
 * Thread safety: Thread-safe. Creates new reader instances per execution.
 *
 * @author Workflow Engine
 * @version 1.0
 */
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
        logger.debug("Creating REST API source reader");
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String url = config.has("url") ? config.get("url").asText() : "";

        if (url.isEmpty()) {
            logger.error("nodeId={}, REST API Source URL is missing", nodeId);
            throw new IllegalArgumentException("nodeType=RestAPISource, nodeId=" + nodeId + ", missing url");
        }

        logger.debug("nodeId={}, Creating REST API reader for URL: {}", nodeId, url);
        return new RestAPIItemReader(url, config, restTemplate, mapper, nodeId);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            logger.debug("Writing {} items to context", items.size());
            context.setVariable("outputItems", new ArrayList<>(items));
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("Validating REST API Source configuration");
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("url")) {
            String nodeId = context.getNodeDefinition().getId();
            logger.error("nodeId={}, REST API Source configuration invalid - missing url", nodeId);
            throw new IllegalArgumentException("nodeType=RestAPISource, nodeId=" + nodeId + ", missing url");
        }
        logger.debug("REST API Source configuration valid");
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return true;
    }

    /**
     * Inner class that reads items from a REST API endpoint.
     * Fetches data on first read() call and returns items one by one.
     *
     * Thread safety: Not thread-safe. Intended for single-threaded batch processing.
     */
    private static class RestAPIItemReader implements ItemReader<Map<String, Object>> {

        private static final Logger readerLogger = LoggerFactory.getLogger(RestAPIItemReader.class);
        private final String url;
        private final JsonNode config;
        private final RestTemplate restTemplate;
        private final ObjectMapper mapper;
        private final String nodeId;
        private Iterator<Map<String, Object>> itemIterator;
        private boolean initialized = false;

        /**
         * Constructs a new REST API item reader.
         *
         * @param url REST API endpoint URL
         * @param config Node configuration with API settings
         * @param restTemplate Spring RestTemplate for HTTP calls
         * @param mapper Jackson ObjectMapper for JSON parsing
         * @param nodeId Node identifier for logging
         */
        public RestAPIItemReader(String url, JsonNode config, RestTemplate restTemplate, ObjectMapper mapper, String nodeId) {
            this.url = url;
            this.config = config;
            this.restTemplate = restTemplate;
            this.mapper = mapper;
            this.nodeId = nodeId;
        }

        @Override
        public Map<String, Object> read() {
            if (!initialized) {
                readerLogger.debug("nodeId={}, Initializing REST API reader", nodeId);
                initialize();
                initialized = true;
            }

            if (itemIterator != null && itemIterator.hasNext()) {
                Map<String, Object> item = itemIterator.next();
                readerLogger.debug("nodeId={}, Read item from API", nodeId);
                return item;
            }
            readerLogger.debug("nodeId={}, No more items available", nodeId);
            return null;
        }

        private void initialize() {
            try {
                readerLogger.info("nodeId={}, Fetching data from REST API: {}", nodeId, url);
                List<Map<String, Object>> items = fetchFromAPI();
                readerLogger.info("nodeId={}, Successfully fetched {} items from REST API", nodeId, items.size());
                itemIterator = items.iterator();
            } catch (Exception e) {
                readerLogger.error("nodeId={}, Failed to fetch from REST API: {}", nodeId, e.getMessage(), e);
                itemIterator = Collections.emptyIterator();
            }
        }

        /**
         * Fetches data from the configured REST API endpoint.
         *
         * @return List of maps representing API response items
         * @throws Exception if API call or parsing fails
         */
        private List<Map<String, Object>> fetchFromAPI() throws Exception {
            String method = config.has("method") ? config.get("method").asText() : "GET";
            String body = config.has("requestBody") ? config.get("requestBody").asText() : null;

            readerLogger.debug("nodeId={}, HTTP method: {}", nodeId, method);

            HttpHeaders headers = new HttpHeaders();
            if (config.has("headers")) {
                JsonNode headersNode = config.get("headers");
                headersNode.fields().forEachRemaining(entry -> {
                    headers.add(entry.getKey(), entry.getValue().asText());
                    readerLogger.debug("nodeId={}, Added header: {}", nodeId, entry.getKey());
                });
            }

            String finalUrl = url;
            if (config.has("queryParams")) {
                finalUrl = appendQueryParams(finalUrl);
            }

            addAuthHeaders(headers);
            readerLogger.debug("nodeId={}, Final URL: {}", nodeId, finalUrl);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            readerLogger.debug("nodeId={}, Sending {} request to {}", nodeId, method, finalUrl);

            try {
                ResponseEntity<String> response = restTemplate.exchange(finalUrl, HttpMethod.valueOf(method), entity, String.class);
                readerLogger.debug("nodeId={}, Response status: {}", nodeId, response.getStatusCode());

                if (response.getBody() == null || response.getBody().isEmpty()) {
                    readerLogger.warn("nodeId={}, Empty response body from API", nodeId);
                    return new ArrayList<>();
                }

                String jsonPath = config.has("jsonPath") ? config.get("jsonPath").asText() : "";
                return parseResponse(response.getBody(), jsonPath);
            } catch (Exception e) {
                readerLogger.error("nodeId={}, REST API call failed: {}", nodeId, e.getMessage(), e);
                throw e;
            }
        }

        /**
         * Appends URL-encoded query parameters to the URL.
         *
         * @param baseUrl Base URL
         * @return URL with appended query parameters
         */
        private String appendQueryParams(String baseUrl) {
            StringBuilder queryString = new StringBuilder();
            JsonNode params = config.get("queryParams");
            boolean first = true;

            for (Iterator<Map.Entry<String, JsonNode>> it = params.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (!first) {
                    queryString.append("&");
                }
                try {
                    String encodedKey = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name());
                    String encodedValue = URLEncoder.encode(entry.getValue().asText(), StandardCharsets.UTF_8.name());
                    queryString.append(encodedKey).append("=").append(encodedValue);
                    first = false;
                    readerLogger.debug("nodeId={}, Added query param: {}={}", nodeId, encodedKey, "***");
                } catch (Exception e) {
                    readerLogger.warn("nodeId={}, Failed to encode query parameter: {}", nodeId, e.getMessage());
                }
            }

            if (queryString.length() == 0) {
                return baseUrl;
            }

            return baseUrl + (baseUrl.contains("?") ? "&" : "?") + queryString.toString();
        }

        /**
         * Adds authentication headers based on configured auth type.
         *
         * @param headers HTTP headers to modify
         */
        private void addAuthHeaders(HttpHeaders headers) {
            if (!config.has("authType")) {
                readerLogger.debug("nodeId={}, No authentication configured", nodeId);
                return;
            }

            String authType = config.get("authType").asText();
            readerLogger.debug("nodeId={}, Adding authentication headers: {}", nodeId, authType);

            try {
                switch (authType) {
                    case "API Key":
                        if (config.has("authKey")) {
                            headers.add("X-API-Key", config.get("authKey").asText());
                            readerLogger.debug("nodeId={}, Added X-API-Key header", nodeId);
                        } else {
                            readerLogger.warn("nodeId={}, API Key auth type selected but authKey is missing", nodeId);
                        }
                        break;
                    case "Bearer Token":
                        if (config.has("authKey")) {
                            headers.add("Authorization", "Bearer " + config.get("authKey").asText());
                            readerLogger.debug("nodeId={}, Added Bearer Token header", nodeId);
                        } else {
                            readerLogger.warn("nodeId={}, Bearer Token auth type selected but authKey is missing", nodeId);
                        }
                        break;
                    case "Basic Auth":
                        if (config.has("authUser") && config.has("authPass")) {
                            String credentials = config.get("authUser").asText() + ":" + config.get("authPass").asText();
                            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                            headers.add("Authorization", "Basic " + encoded);
                            readerLogger.debug("nodeId={}, Added Basic Auth header", nodeId);
                        } else {
                            readerLogger.warn("nodeId={}, Basic Auth selected but credentials are missing", nodeId);
                        }
                        break;
                    default:
                        readerLogger.warn("nodeId={}, Unknown authentication type: {}", nodeId, authType);
                }
            } catch (Exception e) {
                readerLogger.error("nodeId={}, Failed to add authentication headers: {}", nodeId, e.getMessage(), e);
            }
        }

        /**
         * Parses the API response and extracts data according to the configured JSON path.
         *
         * @param body Response body as string
         * @param jsonPath JSON path to data (e.g., "data.items")
         * @return List of maps representing parsed items
         * @throws Exception if parsing fails
         */
        private List<Map<String, Object>> parseResponse(String body, String jsonPath) throws Exception {
            List<Map<String, Object>> results = new ArrayList<>();

            try {
                JsonNode root = mapper.readTree(body);
                readerLogger.debug("nodeId={}, Response parsed successfully", nodeId);

                JsonNode dataNode = root;
                if (!jsonPath.isEmpty()) {
                    readerLogger.debug("nodeId={}, Navigating to JSON path: {}", nodeId, jsonPath);
                    String[] parts = jsonPath.split("\\.");
                    for (String part : parts) {
                        dataNode = dataNode.get(part);
                        if (dataNode == null) {
                            readerLogger.warn("nodeId={}, JSON path not found: {}", nodeId, jsonPath);
                            return results;
                        }
                    }
                }

                if (dataNode.isArray()) {
                    readerLogger.debug("nodeId={}, Response is array with {} items", nodeId, dataNode.size());
                    dataNode.forEach(item -> {
                        try {
                            Map<String, Object> map = mapper.convertValue(item, Map.class);
                            results.add(map);
                        } catch (Exception e) {
                            readerLogger.warn("nodeId={}, Failed to convert array item: {}", nodeId, e.getMessage());
                        }
                    });
                } else if (dataNode.isObject()) {
                    readerLogger.debug("nodeId={}, Response is single object", nodeId);
                    Map<String, Object> map = mapper.convertValue(dataNode, Map.class);
                    results.add(map);
                } else {
                    readerLogger.warn("nodeId={}, Response is neither array nor object: {}", nodeId, dataNode.getNodeType());
                }

                readerLogger.info("nodeId={}, Parsed {} items from response", nodeId, results.size());
                return results;
            } catch (Exception e) {
                readerLogger.error("nodeId={}, Failed to parse API response: {}", nodeId, e.getMessage(), e);
                throw e;
            }
        }
    }
}
