package com.kafkastreams.redisstatestore.restapi.config;

import org.apache.kafka.streams.state.StoreBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.beans.BeanProperty;
import java.util.HashMap;
import java.util.Map;

public class RedisStoreBuilder implements StoreBuilder<RedisStore<String, String>> {

    private boolean enableCaching = true;
    private final String name;
    private final String streamId;

    private final Map<String, String> logConfig = new HashMap<>();
    private boolean loggingEnabled;

    @Autowired
    public RedisStoreBuilder(String name, String streamId, boolean loggingEnabled) {
        this.name = name;
        this.streamId = streamId;
        this.loggingEnabled = loggingEnabled;
    }

    @Override
    public StoreBuilder<RedisStore<String, String>> withCachingEnabled() {
        this.enableCaching = true;
        return this;
    }

    @Override
    public StoreBuilder<RedisStore<String, String>> withCachingDisabled() {
        this.enableCaching = false;
        return this;
    }

    @Override
    public StoreBuilder<RedisStore<String, String>> withLoggingEnabled(Map<String, String> config) {
        loggingEnabled = true;
        return this;
    }

    @Override
    public StoreBuilder<RedisStore<String, String>> withLoggingDisabled() {
        this.loggingEnabled = false;
        return this;
    }

    @Override
    public RedisStore<String, String> build() {
        return new RedisStore<>(this.name, this.streamId, this.loggingEnabled);
    }

    @Override
    public Map<String, String> logConfig() {
        return logConfig;
    }

    @Override
    public boolean loggingEnabled() {
        return loggingEnabled;
    }

    @Override
    public String name() {
        return name;
    }
}