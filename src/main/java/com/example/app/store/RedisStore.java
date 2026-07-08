package com.example.app.store;

import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.StateStoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;

import java.util.HashMap;
import java.util.Map;

public class RedisStore<K, V> implements StateStore, WriteableRedisStore<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(RedisStore.class);
    private final String name;
    private final String streamId;
    private final String redisHost;
    private final int redisPort;
    private boolean open = true;
    private boolean loggingEnabled;
    private boolean flushed;

    public RedisStore(String name, String streamId, String redisHost, int redisPort, boolean loggingEnabled) {
        this.name = name;
        this.streamId = streamId;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.loggingEnabled = loggingEnabled;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public void init(StateStoreContext context, StateStore root) {
        if (root != null) {
            // register the store
            context.register(root, (key, value) -> {
                write(key.toString(), value.toString());
            });
        }

        this.open = true;
    }

    @Override
    public void flush() {
        flushed = true;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean persistent() {
        return true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void write(String key, String value) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            Map<String, String> hash = new HashMap<>();
            hash.put(key, value);
            jedis.xadd(this.streamId, StreamEntryID.NEW_ENTRY, hash);
        } catch (Exception ex) {
            logger.error("Failed due to exception: {}", ex.getMessage());
        }
    }
}
