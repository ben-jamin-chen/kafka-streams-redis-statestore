package com.kafkastreams.redisstatestore.restapi.controller.v1;

import com.kafkastreams.redisstatestore.restapi.config.ReadableRedisStore;
import com.kafkastreams.redisstatestore.restapi.config.RedisStoreType;
import com.kafkastreams.redisstatestore.restapi.dto.MovieAverageRatingResponse;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.servers.Server;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.StreamEntry;

@OpenAPIDefinition(servers = {@Server(url = "http://localhost:7001")}, info = @Info(title = "Sample Spring Boot Kafka Streams API", version = "v1", description = "A demo project using a Redis-backed Kafka Streams state store", license = @License(name = "MIT License", url = "https://github.com/bchen04/kafka-streams-redis-statestore/blob/master/LICENSE"), contact = @Contact(url = "https://www.linkedin.com/in/bchen04/", name = "Ben Chen")))
@RestController
@RequestMapping("v1/movie")
public class MovieController {
    private static final Logger logger = LoggerFactory.getLogger(MovieController.class);
    private final KafkaStreams streams;

    @Value("${redis.state.store.name}")
    private String redisStateStoreName;

    @Value("${redis.streamId}")
    private String redisStreamId;

    @Autowired
    public MovieController(KafkaStreams streams) {
        this.streams = streams;
    }

    @Operation(summary = "Returns the average rating for a particular movie")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "successful operation",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "500", description = "internal server error")})
    @GetMapping(value = "{movieId}/rating", produces = {"application/json"})
    public ResponseEntity<MovieAverageRatingResponse> getMovieAverageRating(@Parameter(description = "Movie identifier",
            required = true, example = "362") @PathVariable Long movieId) {
        try {
            // Get the Redis store type
            final QueryableStoreType<ReadableRedisStore<String, String>> queryableStoreType =
                    new RedisStoreType<String, String>(redisStreamId);

            // Get access to the Redis store
            ReadableRedisStore<String, String> store =
                    streams.store(StoreQueryParameters.fromNameAndType(redisStateStoreName, queryableStoreType));

            // Query the Redis store
            String result = store.read(movieId.toString());

            return ResponseEntity
                    .ok()
                    .body(new MovieAverageRatingResponse(movieId, Double.parseDouble(result)));

        } catch (Exception ex) {
            logger.error("Failed due to exception: {}", ex.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}
