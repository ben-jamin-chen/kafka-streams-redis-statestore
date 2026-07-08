package com.example.app.config;

import com.example.app.store.RedisStore;
import com.example.app.store.RedisStoreBuilder;
import io.confluent.demo.CountAndSum;
import io.confluent.demo.Rating;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.apache.kafka.common.serialization.Serdes.Double;
import static org.apache.kafka.common.serialization.Serdes.Long;
import static org.apache.kafka.streams.kstream.Grouped.with;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {
    private static final Logger logger = LoggerFactory.getLogger(KafkaStreamsConfig.class);

    @Value("${schema.registry.url}")
    private String schemaRegistryUrl;

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

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

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
    public KStream<Long, Rating> ratingStream(StreamsBuilder builder) {
        RedisStoreBuilder customStoreBuilder =
                new RedisStoreBuilder(redisStateStoreName, redisStreamId, redisHost, redisPort, true);

        builder.addStateStore(customStoreBuilder);

        KStream<Long, Rating> ratingStream = builder.stream(ratingTopicName,
                Consumed.with(Long(), getRatingSerde(schemaRegistryUrl)));

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
                .process(() -> new Processor<Long, Double, Void, Void>() {
                    private RedisStore<String, String> stateStore;

                    @Override
                    public void init(final ProcessorContext<Void, Void> context) {
                        stateStore = context.getStateStore(redisStateStoreName);
                    }

                    @Override
                    public void process(final Record<Long, Double> record) {
                        logger.info("Key: {} Value: {}", record.key(), record.value());
                        stateStore.write(record.key().toString(), record.value().toString());
                    }
                }, redisStateStoreName);

        return ratingStream;
    }
}
