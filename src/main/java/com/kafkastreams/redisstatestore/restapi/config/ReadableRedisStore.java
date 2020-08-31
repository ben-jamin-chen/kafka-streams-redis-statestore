package com.kafkastreams.redisstatestore.restapi.config;

// Read-only interface for MyCustomStore
public interface ReadableRedisStore<K,V> {
    String read(String key);
}
