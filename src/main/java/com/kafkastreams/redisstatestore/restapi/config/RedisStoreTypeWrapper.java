package com.kafkastreams.redisstatestore.restapi.config;

import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.internals.StateStoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntry;
import redis.clients.jedis.StreamEntryID;
import java.util.List;
import java.util.Map;

public class RedisStoreTypeWrapper<K, V> implements ReadableRedisStore<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(RedisStoreTypeWrapper.class);
    private final QueryableStoreType<ReadableRedisStore<String, String>> redisStoreType;
    private final String storeName;
    private final String streamId;
    private final StateStoreProvider provider;

    public RedisStoreTypeWrapper(final StateStoreProvider provider,
                                 final String storeName,
                                 final String streamId,
                                 final QueryableStoreType<ReadableRedisStore<String, String>> redisStoreType) {
        this.provider = provider;
        this.storeName = storeName;
        this.streamId = streamId;
        this.redisStoreType = redisStoreType;
    }

    @Override
    public String read(String key) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            StreamEntryID start = null; // null -> start from the last item in the stream
            StreamEntryID end = new StreamEntryID(0, 0); // end at first item in the stream
            int count = 1;

            //query the stream range and return by inverted order
            List<StreamEntry> streamEntries = jedis.xrevrange(this.streamId, start, end, count);

            if (streamEntries != null) {
                // Get the most recently added item, which is also the last item
                StreamEntry entry = streamEntries.get(streamEntries.size() - 1);
                Map<String, String> fields = entry.getFields();
                return fields.get(key);
            } else {
                logger.warn("No new data in the stream.");
            }
        } catch (Exception ex) {
            logger.error("Failed due to exception: {}", ex.getMessage());
        }

        return null;
    }
}
