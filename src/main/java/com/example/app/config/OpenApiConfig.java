package com.example.app.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(servers = {@Server(url = "http://localhost:7001")}, info = @Info(title = "Sample Spring Boot Kafka Streams API", version = "v1", description = "A demo project using a Redis-backed Kafka Streams state store", license = @License(name = "MIT License", url = "https://github.com/ben-jamin-chen/kafka-streams-redis-statestore/blob/main/LICENSE"), contact = @Contact(url = "https://www.linkedin.com/in/ben-jamin-chen/", name = "Ben Chen")))
@Configuration
public class OpenApiConfig {
}
