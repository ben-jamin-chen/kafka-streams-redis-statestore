package com.example.app.store;

public interface ReadableRedisStore<K, V> {
    String read(String key);
}
