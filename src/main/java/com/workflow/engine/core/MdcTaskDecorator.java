package com.workflow.engine.core;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Task decorator that preserves SLF4J Mapped Diagnostic Context (MDC) across async thread boundaries.
 *
 * When using async task execution with Spring ThreadPoolTaskExecutor, MDC context (such as
 * request IDs, correlation IDs, user information) is lost when execution moves to a different
 * thread. This decorator captures the current MDC context before async execution and restores
 * it in the new thread, ensuring log correlation IDs remain consistent across thread boundaries.
 *
 * Thread safety: Thread-safe. Safe to use in concurrent executor environments.
 *
 * @author Workflow Engine
 * @version 1.0
 */
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * Decorates the provided runnable to preserve MDC context across thread boundaries.
     *
     * Captures the current MDC context map before task execution. When the runnable executes
     * in a different thread, the captured context is restored, ensuring consistent log
     * correlation throughout the task execution. MDC is cleared after task completion.
     *
     * @param runnable the original task to decorate
     * @return a decorated runnable that preserves MDC context
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            if (mdcContext != null) {
                mdcContext.forEach(MDC::put);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
