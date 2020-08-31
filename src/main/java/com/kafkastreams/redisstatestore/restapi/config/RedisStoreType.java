package com.kafkastreams.redisstatestore.restapi.config;

import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.internals.StateStoreProvider;
import org.springframework.stereotype.Component;

public class RedisStoreType<K, V> implements QueryableStoreType<ReadableRedisStore<String, String>> {
    private String streamId;

    public RedisStoreType(String streamId)
    {
        this.streamId = streamId;
    }

    // Only accept StateStores that are of type RedisStore
    @Override
    public boolean accepts(StateStore stateStore) {
        return stateStore instanceof RedisStore;
    }

    @Override
    public ReadableRedisStore<String, String> create(final StateStoreProvider storeProvider, final String storeName) {
        return new RedisStoreTypeWrapper<>(storeProvider, storeName, streamId, this);
    }
}