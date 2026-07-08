package com.example.app.store;

import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.internals.StateStoreProvider;

public class RedisStoreType<K, V> implements QueryableStoreType<ReadableRedisStore<String, String>> {
    private final String streamId;
    private final String redisHost;
    private final int redisPort;

    public RedisStoreType(String streamId, String redisHost, int redisPort) {
        this.streamId = streamId;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    // Only accept StateStores that are of type RedisStore
    @Override
    public boolean accepts(StateStore stateStore) {
        return stateStore instanceof RedisStore;
    }

    @Override
    public ReadableRedisStore<String, String> create(final StateStoreProvider storeProvider, final String storeName) {
        return new RedisStoreTypeWrapper<>(storeProvider, storeName, streamId, redisHost, redisPort, this);
    }
}
