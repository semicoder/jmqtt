package org.jmqtt.persistence;

import org.jmqtt.session.IMatchingCondition;
import org.jmqtt.session.IMessagesStore;
import org.jmqtt.session.model.Message;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by gavin on 16/9/13.
 */
public class RedissonMessageStore implements IMessagesStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedissonMessageStore.class);

    private RedissonClient redisson;

    //maps clientId -> guid
    private RMap<String, Message> retainedStore;
    private final Map<String, Object> redissonCache;

    RedissonMessageStore(RedissonClient redisson, Map<String, Object> redissonCache) {
        this.redisson = redisson;
        this.redissonCache = redissonCache;
    }

    @Override
    public void initStore() {
        retainedStore = redisson.getMap("retained");
    }

    @Override
    public void storeRetained(String topic, Message message) {
        retainedStore.put(topic, message);
    }

    @Override
    public Collection<Message> searchMatching(IMatchingCondition condition) {
        LOG.debug("searchMatching scanning all retained packets, presents are {}", retainedStore.size());

        List<Message> results = new ArrayList<>();

        for (String key : retainedStore.keySet()) {

            if (condition.match(key)) {
                final Message message = retainedStore.get(key);
                results.add(message);
            }

        }

        return results;
    }

    @Override
    public void storePublishForFuture(Message evt) {
        LOG.debug("storePublishForFuture store evt {}", evt);
        if (evt.getClientId() == null) {
            LOG.error("persisting a message without a clientId, bad programming error msg: {}", evt);
            throw new IllegalArgumentException("\"persisting a message without a clientId, bad programming error");
        }

        RMap<String, Message> map = getStoredMessage(evt.getClientId());
        map.put(evt.getMsgId(), evt);
        RMap<Integer, String> messageIdToGuid = getIdMappingMap(evt.getClientId());
        messageIdToGuid.put(evt.getPacketId(), evt.getMsgId());
    }

    @Override
    public void removeStoredMessage(Message evt) {
        RMap<String, Message> map = getStoredMessage(evt.getClientId());
        map.remove(evt.getMsgId());
        RMap<Integer, String> messageIdToGuid = getIdMappingMap(evt.getClientId());
        messageIdToGuid.remove(evt.getPacketId());
    }

    @Override
    public void cacheForExactly(Message evt) {
        LOG.debug("cacheForExactly store evt {}", evt);
        if (evt.getClientId() == null) {
            LOG.error("persisting a message without a clientId, bad programming error msg: {}", evt);
            throw new IllegalArgumentException("\"persisting a message without a clientId, bad programming error");
        }

        RMap<String, Message> map = getCacheExactlyMessage(evt.getClientId());
        map.put(evt.getMsgId(), evt);
        RMap<Integer, String> messageIdToGuid = getIdMappingMap(evt.getClientId());
        messageIdToGuid.put(evt.getPacketId(), evt.getMsgId());
    }

    @Override
    public void removeCacheExactlyMessage(Message evt) {
        RMap<String, Message> map = getCacheExactlyMessage(evt.getClientId());
        map.remove(evt.getMsgId());
        RMap<Integer, String> messageIdToGuid = getIdMappingMap(evt.getClientId());
        messageIdToGuid.remove(evt.getPacketId());
    }

    @Override
    public List<Message> listMessagesInSession(String clientId, Collection<String> guids) {
        List<Message> ret = new ArrayList<>();

        RMap<String, Message> map = getStoredMessage(clientId);

        for (String guid : guids) {
            ret.add(map.get(guid));
        }
        return ret;
    }

    @Override
    public void dropMessagesInSession(String clientId) {
        getIdMappingMap(clientId).clear();
        retainedStore.remove(clientId);
    }

    @Override
    public Message getMessageByGuid(String clientId, String guid) {
        RMap<String, Message> map = getStoredMessage(clientId);
        return map.get(guid);
    }

    @Override
    public Message getCacheMessageByGuid(String clientId, String guid) {
        RMap<String, Message> map = getCacheExactlyMessage(clientId);
        return map.get(guid);
    }

    @Override
    public void cleanRetained(String topic) {
        retainedStore.remove(topic);
    }

    private RMap<Integer, String> getIdMappingMap(String clientId) {
        RMap<Integer, String> rMap = (RMap<Integer, String>) redissonCache.get(RedissonSessionsStore.KEY_ID_MAPPING + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap(RedissonSessionsStore.KEY_ID_MAPPING + clientId);
        redissonCache.put(RedissonSessionsStore.KEY_ID_MAPPING + clientId, rMap);

        return rMap;
    }

    private RMap<String, Message> getStoredMessage(String clientId) {
        RMap<String, Message> rMap = (RMap<String, Message>) redissonCache.get("storedMessage:" + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap("storedMessage:" + clientId);

        redissonCache.put("storedMessage:" + clientId, rMap);

        return rMap;
    }

    private RMap<String, Message> getCacheExactlyMessage(String clientId) {
        RMap<String, Message> rMap = (RMap<String, Message>) redissonCache.get("cacheExactly:" + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap("cacheExactly:" + clientId);

        redissonCache.put("cacheExactly:" + clientId, rMap);

        return rMap;
    }

}
