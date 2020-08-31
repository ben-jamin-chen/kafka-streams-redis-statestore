package com.kafkastreams.redisstatestore.restapi.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.confluent.demo.CountAndSum;
import io.confluent.demo.Rating;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.util.Optional.ofNullable;
import static org.apache.kafka.common.serialization.Serdes.Double;
import static org.apache.kafka.common.serialization.Serdes.Long;
import static org.apache.kafka.streams.StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG;
import static org.apache.kafka.streams.kstream.Grouped.with;

@Configuration
public class KafkaStreamsConfig {
    @Value("${schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${app.server.config}")
    private String appServerConfig;

    @Value("${application.name}")
    private String appName;

    @Value("${rating.topic.name}")
    private String ratingTopicName;

    @Value("${average.rating.topic.name}")
    private String avgRatingsTopicName;

    @Value("${state.store.name}")
    private String stateStoreName;

    @Value("${redis.state.store.name}")
    private String redisStateStoreName;

    @Value("${redis.streamId}")
    private String redisStreamId;

    private static SpecificAvroSerde<CountAndSum> getCountAndSumSerde(String schemaRegistryUrl) {
        SpecificAvroSerde<CountAndSum> serde = new SpecificAvroSerde<>();
        serde.configure(getSerdeConfig(schemaRegistryUrl), false);
        return serde;
    }

    private static SpecificAvroSerde<Rating> getRatingSerde(String schemaRegistryUrl) {
        SpecificAvroSerde<Rating> serde = new SpecificAvroSerde<>();
        serde.configure(getSerdeConfig(schemaRegistryUrl), false);
        return serde;
    }

    private static Map<String, String> getSerdeConfig(String schemaRegistryUrl) {
        final HashMap<String, String> map = new HashMap<>();
        map.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                ofNullable(schemaRegistryUrl).orElse(""));
        return map;
    }

    @Bean
    @Primary
    public KafkaStreams kafkaStreams(KafkaProperties kafkaProperties) {
        final Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appName);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Long().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Double().getClass());
        props.put(StreamsConfig.STATE_DIR_CONFIG, "data");
        props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, appServerConfig);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, JsonNode.class);
        props.put(DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndContinueExceptionHandler.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Topology topology = this.buildTopology(new StreamsBuilder());

        KafkaStreams kafkaStreams = new KafkaStreams(topology, props);
        kafkaStreams.start();

        return kafkaStreams;
    }

    private Topology buildTopology(StreamsBuilder builder) {
        RedisStoreBuilder customStoreBuilder =
                new RedisStoreBuilder(redisStateStoreName, redisStreamId, true);

        builder.addStateStore(customStoreBuilder);

        KStream<Long, Rating> ratingStream = builder.stream(ratingTopicName,
                Consumed.with(Serdes.Long(), getRatingSerde(schemaRegistryUrl)));

        SpecificAvroSerde<CountAndSum> countAndSumSerde = getCountAndSumSerde(schemaRegistryUrl);

        // Grouping Ratings
        KGroupedStream<Long, Double> ratingsById = ratingStream
                .map((key, rating) -> new KeyValue<>(rating.getMovieId(), rating.getRating()))
                .groupByKey(with(Long(), Double()));

        final KTable<Long, CountAndSum> ratingCountAndSum =
                ratingsById.aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> {
                            aggregate.setCount(aggregate.getCount() + 1);
                            aggregate.setSum(aggregate.getSum() + value);
                            return aggregate;
                        },
                        Materialized.with(Long(), countAndSumSerde));

        final KTable<Long, Double> ratingAverage =
                ratingCountAndSum.mapValues(value -> value.getSum() / value.getCount(),
                        Materialized.<Long, Double, KeyValueStore<Bytes, byte[]>>as(stateStoreName)
                                .withKeySerde(Long())
                                .withValueSerde(Double()));

        // persist the result in topic
        //ratingAverage.toStream().to(avgRatingsTopicName);

        // define the stream processor that will process one record at a time, and connect the processor
        // with the associated Redis state store
        ratingAverage
                .toStream()
                .process(() -> new Processor<>() {
                    RedisStore<byte[], byte[]> stateStore;

                    @SuppressWarnings("unchecked")
                    @Override
                    public void init(final ProcessorContext context) {
                        stateStore = (RedisStore<byte[], byte[]>) context.getStateStore(redisStateStoreName);
                    }

                    @Override
                    public void process(final Long key, final Double value) {
                        System.out.println("Key: " + key.toString() + " Value: " + value.toString());
                        stateStore.write(key.toString(), value.toString());
                    }

                    @Override
                    public void close() {
                    }
                }, redisStateStoreName);


        // finish the topology
        return builder.build();
    }
}
