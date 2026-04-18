package org.example.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Test Service API",
                version = "1.0",
                description = "OpenAPI documentation for the sample controllers (Messages repository APIs and OpenSearch APIs).",
                contact = @Contact(name = "API Support", email = "support@example.com")
        ),
        servers = {
                @Server(url = "http://localhost:8180", description = "Local Server")
        }
)
@Configuration
public class OpenApiConfig {
    // Additional SpringDoc configuration can go here if needed
}
