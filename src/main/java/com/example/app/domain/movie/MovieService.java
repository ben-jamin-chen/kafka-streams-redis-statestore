package com.example.app.domain.movie;

import com.example.app.store.ReadableRedisStore;
import com.example.app.store.RedisStoreType;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

@Service
public class MovieService {
    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    @Value("${redis.state.store.name}")
    private String redisStateStoreName;

    @Value("${redis.streamId}")
    private String redisStreamId;

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    public MovieService(StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
    }

    public Double getAverageRating(Long movieId) {
        final QueryableStoreType<ReadableRedisStore<String, String>> queryableStoreType =
                new RedisStoreType<String, String>(redisStreamId, redisHost, redisPort);

        final KafkaStreams streams = streamsBuilderFactoryBean.getKafkaStreams();

        ReadableRedisStore<String, String> store =
                streams.store(StoreQueryParameters.fromNameAndType(redisStateStoreName, queryableStoreType));

        return Double.parseDouble(store.read(movieId.toString()));
    }
}
