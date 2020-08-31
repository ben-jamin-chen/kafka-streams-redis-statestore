package com.kafkastreams.redisstatestore.restapi.config;

// Read-write interface for RedisStore
public interface WriteableRedisStore<K,V> {
    void write(String key, String value);
}