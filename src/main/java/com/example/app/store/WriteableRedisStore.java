package com.example.app.store;

public interface WriteableRedisStore<K, V> {
    void write(String key, String value);
}
