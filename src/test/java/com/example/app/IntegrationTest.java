package com.example.app;

import com.example.app.domain.movie.dto.MovieAverageRatingResponse;
import io.confluent.demo.Rating;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IntegrationTest {
    private static final Network network = Network.newNetwork();

    @Container
    @ServiceConnection
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.3.0")
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092");

    @Container
    static final GenericContainer<?> schemaRegistry = new GenericContainer<>("confluentinc/cp-schema-registry:8.3.0")
            .withNetwork(network)
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka:19092")
            .waitingFor(Wait.forHttp("/subjects").forPort(8081))
            .dependsOn(kafka);

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("schema.registry.url", IntegrationTest::schemaRegistryUrl);
        registry.add("redis.host", redis::getHost);
        registry.add("redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.streams.state-dir", () -> "target/integration-test-state");
    }

    private static String schemaRegistryUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    }

    @Test
    void averageRatingFlowsFromKafkaThroughRedisToTheRestApi() {
        try (KafkaProducer<Long, Rating> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl()))) {
            producer.send(new ProducerRecord<>("ratings", 362L, new Rating(362L, 10.0)));
            producer.send(new ProducerRecord<>("ratings", 362L, new Rating(362L, 8.0)));
            producer.flush();
        }

        RestClient restClient = RestClient.create("http://localhost:" + port);

        await().atMost(Duration.ofMinutes(2)).ignoreExceptions().untilAsserted(() -> {
            MovieAverageRatingResponse response = restClient.get()
                    .uri("/v1/movie/362/rating")
                    .retrieve()
                    .body(MovieAverageRatingResponse.class);

            assertThat(response).isNotNull();
            assertThat(response.rating()).isEqualTo(9.0);
        });
    }
}
