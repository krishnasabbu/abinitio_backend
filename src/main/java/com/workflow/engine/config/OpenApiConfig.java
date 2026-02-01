package com.workflow.engine.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Workflow Engine API",
                version = "1.0.0",
                description = "A comprehensive REST API for managing and executing data workflow orchestration. " +
                        "Supports workflow creation, execution monitoring, connection management, logging, and metrics collection.",
                contact = @Contact(
                        name = "Workflow Engine Team",
                        email = "support@workflow-engine.com"
                ),
                license = @License(
                        name = "MIT"
                )
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT authentication"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Workflow Engine API")
                        .version("1.0.0")
                        .description(
                                "A comprehensive REST API for managing and executing data workflow orchestration. " +
                                        "Supports workflow creation, execution monitoring, connection management, logging, and metrics collection."
                        )
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("Workflow Engine Team")
                                .email("support@workflow-engine.com")
                        )
                        .license(new io.swagger.v3.oas.models.info.License()
                                .name("MIT")
                        )
                );
    }
}
