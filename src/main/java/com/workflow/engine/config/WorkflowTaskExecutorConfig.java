package com.workflow.engine.config;

import com.workflow.engine.core.MdcTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for the shared ThreadPoolTaskExecutor used by DynamicJobBuilder.
 *
 * Provides a configurable, MDC-aware thread pool for parallel branch execution
 * in workflow splits. This replaces per-split SimpleAsyncTaskExecutor instances
 * with a shared, bounded pool.
 *
 * Configuration properties (application.properties):
 * - workflow.executor.core-pool-size: Minimum threads (default: 4)
 * - workflow.executor.max-pool-size: Maximum threads (default: 16)
 * - workflow.executor.queue-capacity: Task queue size (default: 100)
 * - workflow.executor.thread-name-prefix: Thread naming (default: "wf-")
 * - workflow.executor.await-termination-seconds: Graceful shutdown wait (default: 60)
 *
 * Thread Safety: Thread-safe. Singleton bean shared across all job executions.
 *
 * @see DynamicJobBuilder
 * @see MdcTaskDecorator
 */
@Configuration
public class WorkflowTaskExecutorConfig {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskExecutorConfig.class);

    @Value("${workflow.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${workflow.executor.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${workflow.executor.queue-capacity:100}")
    private int queueCapacity;

    @Value("${workflow.executor.thread-name-prefix:wf-}")
    private String threadNamePrefix;

    @Value("${workflow.executor.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    @Value("${workflow.executor.allow-core-thread-timeout:true}")
    private boolean allowCoreThreadTimeout;

    @Bean(name = "workflowTaskExecutor")
    public TaskExecutor workflowTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeout);

        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);

        executor.initialize();

        logger.info("Initialized workflow TaskExecutor: corePool={}, maxPool={}, " +
                   "queueCapacity={}, prefix='{}'",
            corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);

        return executor;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    private static class LoggingCallerRunsPolicy implements RejectedExecutionHandler {
        private static final Logger log = LoggerFactory.getLogger(LoggingCallerRunsPolicy.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                log.warn("Workflow executor queue full (size={}). Running task in caller thread. " +
                        "Consider increasing workflow.executor.queue-capacity or max-pool-size.",
                    executor.getQueue().size());
                r.run();
            } else {
                log.error("Workflow executor shutdown - rejecting task");
                throw new java.util.concurrent.RejectedExecutionException(
                    "Workflow executor has been shutdown");
            }
        }
    }
}
