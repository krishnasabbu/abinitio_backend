package com.workflow.engine.core;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

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
