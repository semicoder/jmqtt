package org.jmqtt.persistence;

import org.jmqtt.session.IMessagesStore;
import org.jmqtt.session.ISessionsStore;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by gavin on 16/9/13.
 */
public class RedissonPersistentStore {

    public Map<String, Object> redissonCache = new ConcurrentHashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(RedissonPersistentStore.class);

    private RedissonClient db;

    public RedissonPersistentStore(Config config) {
        db = Redisson.create(config);
    }

    /**
     * Factory method to create message store backed by MapDB
     */
    public IMessagesStore messagesStore() {
        IMessagesStore msgStore = new RedissonMessageStore(db, redissonCache);
        msgStore.initStore();
        return msgStore;
    }

    public ISessionsStore sessionsStore(IMessagesStore msgStore) {
        ISessionsStore sessionsStore = new RedissonSessionsStore(db, redissonCache, msgStore);
        sessionsStore.initStore();
        return sessionsStore;
    }

    public void close() {
        db.shutdown();
    }
}
