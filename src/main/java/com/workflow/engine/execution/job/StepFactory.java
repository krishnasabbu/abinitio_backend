package com.workflow.engine.execution.job;

import com.workflow.engine.api.persistence.PersistenceStepListener;
import com.workflow.engine.execution.NodeExecutionContext;
import com.workflow.engine.execution.NodeExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.routing.EdgeBufferStore;
import com.workflow.engine.execution.routing.OutputPort;
import com.workflow.engine.execution.routing.RoutingContext;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import com.workflow.engine.graph.StepNode;
import com.workflow.engine.metrics.MetricsCollectionListener;
import com.workflow.engine.metrics.MetricsCollector;
import com.workflow.engine.model.FailureAction;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.repository.NodeOutputDataRepository;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component
public class StepFactory {

    private static final Logger logger = LoggerFactory.getLogger(StepFactory.class);

    private final NodeExecutorRegistry executorRegistry;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MetricsCollector metricsCollector;
    private final NodeOutputDataRepository outputDataRepository;
    private JdbcTemplate jdbcTemplate;
    private String executionId;

    public StepFactory(NodeExecutorRegistry executorRegistry,
                      JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      MetricsCollector metricsCollector,
                      NodeOutputDataRepository outputDataRepository) {
        this.executorRegistry = executorRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.metricsCollector = metricsCollector;
        this.outputDataRepository = outputDataRepository;
    }

    public void setApiListenerContext(JdbcTemplate jdbcTemplate, String executionId) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionId = executionId;
        logger.info("API Listener context set: jdbcTemplate={}, executionId={}",
            jdbcTemplate != null ? "present" : "null", executionId);
    }

    public String getExecutionId() {
        return this.executionId;
    }

    private String normalize(String nodeType) {
        return nodeType == null ? "" : nodeType.trim();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Step buildStep(StepNode stepNode) {
        return buildStep(stepNode, null, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Step buildStep(StepNode stepNode, EdgeBufferStore bufferStore, String executionId) {
        String normalizedNodeType = normalize(stepNode.nodeType());
        logger.debug("Building step for nodeId='{}' with normalized nodeType='{}'",
            stepNode.nodeId(), normalizedNodeType);

        NodeExecutor executor = executorRegistry.getExecutor(normalizedNodeType);
        logger.debug("Resolved executor for nodeType='{}': {}",
            normalizedNodeType, executor.getClass().getSimpleName());

        NodeDefinition nodeDefinition = createNodeDefinition(stepNode);
        NodeExecutionContext context;

        if (bufferStore != null && executionId != null) {
            List<OutputPort> outputPorts = stepNode.outputPorts() != null ? stepNode.outputPorts() : List.of();
            RoutingContext routingContext = new RoutingContext(executionId, stepNode.nodeId(), outputPorts, bufferStore);
            context = new RoutingNodeExecutionContext(nodeDefinition, null, routingContext);
            logger.info("Created RoutingNodeExecutionContext for node={} with {} output ports: {}",
                stepNode.nodeId(), outputPorts.size(),
                outputPorts.stream()
                    .map(p -> p.sourcePort() + "->" + p.targetNodeId() + ":" + p.targetPort())
                    .toList());
        } else {
            context = new NodeExecutionContext(nodeDefinition, null);
            logger.debug("Created regular NodeExecutionContext for node={} (no buffer store)", stepNode.nodeId());
        }

        executor.validate(context);

        ItemReader reader = executor.createReader(context);
        ItemProcessor processor = executor.createProcessor(context);
        ItemWriter writer = executor.createWriter(context);

        int chunkSize = determineChunkSize(stepNode);

        StepBuilder stepBuilder = new StepBuilder(stepNode.nodeId(), jobRepository);

        var chunkBuilder = stepBuilder
            .chunk(chunkSize, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer);

        if (stepNode.metrics() != null && stepNode.metrics().isEnabled()) {
            MetricsCollectionListener listener = new MetricsCollectionListener(
                stepNode.nodeId(),
                stepNode.nodeType(),
                stepNode.metrics(),
                metricsCollector
            );
            chunkBuilder.listener(listener);
        }

        logger.debug("Checking persistence listener for node '{}': this.jdbcTemplate={}, param.executionId={}",
            stepNode.nodeId(), this.jdbcTemplate != null ? "present" : "null", executionId);

        if (this.jdbcTemplate != null && executionId != null) {
            PersistenceStepListener persistenceListener = new PersistenceStepListener(
                this.jdbcTemplate,
                stepNode,
                executionId,
                context,
                outputDataRepository
            );
            chunkBuilder.listener(persistenceListener);
            logger.info("Added PersistenceStepListener for node '{}' with executionId '{}'", stepNode.nodeId(), executionId);
        } else {
            logger.warn("PersistenceStepListener NOT added for node '{}': jdbcTemplate={}, executionId={}",
                stepNode.nodeId(), this.jdbcTemplate != null ? "present" : "null", executionId);
        }

        if (stepNode.exceptionHandling() != null) {
            applyExceptionHandling(chunkBuilder, stepNode);
        }

        return chunkBuilder.build();
    }

    private boolean hasMultipleOutputs(StepNode stepNode) {
        List<OutputPort> ports = stepNode.outputPorts();
        return ports != null && ports.size() > 1;
    }

    private int determineChunkSize(StepNode stepNode) {
        if (stepNode.executionHints() != null && stepNode.executionHints().getChunkSize() != null) {
            return stepNode.executionHints().getChunkSize();
        }
        return 1000;
    }

    @SuppressWarnings("rawtypes")
    private void applyExceptionHandling(org.springframework.batch.core.step.builder.SimpleStepBuilder chunkBuilder,
                                        StepNode stepNode) {
        var policy = stepNode.exceptionHandling();

        if (policy.getMaxRetries() != null && policy.getMaxRetries() > 0) {
            chunkBuilder.faultTolerant()
                .retryLimit(policy.getMaxRetries())
                .retry(Exception.class);
        }

        if (policy.getAction() == FailureAction.SKIP || policy.isSkipOnError()) {
            chunkBuilder.faultTolerant()
                .skipLimit(Integer.MAX_VALUE)
                .skip(Exception.class);
        }
    }

    private NodeDefinition createNodeDefinition(StepNode stepNode) {
        NodeDefinition nodeDefinition = new NodeDefinition();
        nodeDefinition.setId(stepNode.nodeId());
        nodeDefinition.setType(stepNode.nodeType());
        nodeDefinition.setConfig(stepNode.config());
        nodeDefinition.setMetrics(stepNode.metrics());
        nodeDefinition.setOnFailure(stepNode.exceptionHandling());
        nodeDefinition.setExecutionHints(stepNode.executionHints());
        return nodeDefinition;
    }
}
