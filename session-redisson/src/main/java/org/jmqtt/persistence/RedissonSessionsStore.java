package org.jmqtt.persistence;

import org.jmqtt.session.ClientSession;
import org.jmqtt.session.IMessagesStore;
import org.jmqtt.session.ISessionsStore;
import org.jmqtt.session.model.ClientTopicCouple;
import org.jmqtt.session.model.Message;
import org.jmqtt.session.model.SessionStatus;
import org.jmqtt.session.model.Subscription;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by gavin on 16/9/13.
 */
public class RedissonSessionsStore implements ISessionsStore {

    public static final String KEY_SUBSCRIPTIONS = "subscriptions:";
    public static final String KEY_INFLIGHT_PACKETID_GUID = "inflight_packetid_guid:";
    public static final String KEY_SECOND_PHASE = "pubrelWaiting:";
    public static final String KEY_INFLIGHT_PACKETID = "inflight_packetid:";
    public static final String KEY_SESSION_QUEUE = "session_queue:";
    public static final String KEY_ID_MAPPING = "id_mapping:";
    public static final String KEY_SESSIONS = "sessions";

    private static final Logger LOG = LoggerFactory.getLogger(RedissonSessionsStore.class);

    private RMap<String, SessionStatus> persistentSessions;

    private final RedissonClient redisson;
    private final IMessagesStore messagesStore;
    private final Map<String, Object> redissonCache;

    RedissonSessionsStore(RedissonClient redisson, Map<String, Object> redissonCache, IMessagesStore messagesStore) {
        this.redisson = redisson;
        this.messagesStore = messagesStore;
        this.redissonCache = redissonCache;
    }

    @Override
    public void initStore() {
        persistentSessions = redisson.getMap(KEY_SESSIONS);
    }

    @Override
    public void addNewSubscription(Subscription newSubscription) {
        LOG.debug("addNewSubscription invoked with subscription {}", newSubscription);
        final String clientId = newSubscription.getClientId();
        getSubscriptions(clientId).put(newSubscription.getTopicFilter(), newSubscription);

        if (LOG.isTraceEnabled()) {
            LOG.trace("subscriptions:{}: {}", clientId, getSubscriptions(clientId));
        }
    }

    @Override
    public void removeSubscription(String topicFilter, String clientId) {
        LOG.debug("removeSubscription topic filter: {} for clientId: {}", topicFilter, clientId);
        if (!getSubscriptions(clientId).isExists()) {
            return;
        }
        getSubscriptions(clientId).remove(topicFilter);
    }

    @Override
    public void wipeSubscriptions(String sessionId) {
        LOG.debug("wipeSubscriptions");
        if (LOG.isTraceEnabled()) {
            LOG.trace("Subscription pre wipe: subscriptions:{}: {}", sessionId, getSubscriptions(sessionId));
        }
        getSubscriptions(sessionId).delete();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Subscription post wipe: subscriptions:{}: {}", sessionId, getSubscriptions(sessionId));
        }
    }

    @Override
    public List<ClientTopicCouple> listAllSubscriptions() {
        final List<ClientTopicCouple> allSubscriptions = new ArrayList<>();
        for (String clientId : persistentSessions.keySet()) {
            ConcurrentMap<String, Subscription> clientSubscriptions = getSubscriptions(clientId);
            for (String topicFilter : clientSubscriptions.keySet()) {
                allSubscriptions.add(new ClientTopicCouple(clientId, topicFilter));
            }
        }
        LOG.debug("retrieveAllSubscriptions returning subs {}", allSubscriptions);
        return allSubscriptions;
    }

    @Override
    public Subscription getSubscription(ClientTopicCouple couple) {
        RMap<String, Subscription> clientSubscriptions = getSubscriptions(couple.clientId);
        LOG.debug("subscriptions:{}: {}", couple.clientId, clientSubscriptions);
        return clientSubscriptions.get(couple.topicFilter);
    }

    @Override
    public boolean contains(String clientId) {
        return getSubscriptions(clientId).isExists();
    }

    @Override
    public ClientSession createNewSession(String clientId, boolean cleanSession) {
        LOG.debug("createNewSession for client <{}> with clean flag <{}>", clientId, cleanSession);
        if (persistentSessions.containsKey(clientId)) {
            LOG.error("already exists a session for client <{}>, bad condition", clientId);
            throw new IllegalArgumentException("Can't create a session with the ID of an already existing" + clientId);
        }
        LOG.debug("clientId {} is a newcome, creating it's empty subscriptions set", clientId);
        SessionStatus sessionStatus = new SessionStatus(cleanSession);
        persistentSessions.putIfAbsent(clientId, sessionStatus);
        return new ClientSession(clientId, messagesStore, this, sessionStatus);
    }

    @Override
    public ClientSession sessionForClient(String clientId) {
        if (!persistentSessions.containsKey(clientId)) {
            return null;
        }

        SessionStatus sessionStatus = persistentSessions.get(clientId);
        return new ClientSession(clientId, messagesStore, this, sessionStatus);
    }

    @Override
    public void updateStatus(String clientId, SessionStatus sessionStatus) {
        persistentSessions.put(clientId, sessionStatus);
    }

    /**
     * Return the next valid packetIdentifier for the given client session.
     */
    @Override
    public synchronized int nextPacketId(String clientId) {
        RSet<Integer> inFlightForClient = getInflightPacketId(clientId);
        int maxId = inFlightForClient.isEmpty() ? 0 : Collections.max(inFlightForClient);
        int nextPacketId = (maxId + 1) % 0xFFFF;
        inFlightForClient.add(nextPacketId);
        return nextPacketId;
    }

    @Override
    public synchronized void inFlightAck(String clientId, int packetId) {
        LOG.info("acknowledging inflight clientId <{}> packetId {}", clientId, packetId);
        RMap<Integer, String> m = getInflightIdMapping(clientId);
        if (m == null) {
            LOG.error("Can't find the inFlight record for client <{}>", clientId);
            return;
        }
        m.remove(packetId);

        //remove from the ids store
        RSet<Integer> inFlightForClient = getInflightPacketId(clientId);
        if (inFlightForClient != null) {
            inFlightForClient.remove(packetId);
        }
    }

    @Override
    public void inFlight(String clientId, int packetId, String guid) {
        RMap<Integer, String> m = getInflightIdMapping(clientId);
        m.put(packetId, guid);
        LOG.info("storing inflight clientId <{}> packetId {} guid <{}>", clientId, packetId, guid);
    }

    @Override
    public void bindToDeliver(String guid, String clientId) {
        RList<String> guids = getSessionQueue(clientId);
        guids.add(guid);
    }

    @Override
    public Collection<String> enqueued(String clientId) {
        return getSessionQueue(clientId);
    }

    @Override
    public void removeEnqueued(String clientId, String guid) {
        RList<String> guids = getSessionQueue(clientId);
        guids.remove(guid);
    }

    @Override
    public synchronized void pubrelWaiting(String clientId, int packetId) {
        LOG.info("acknowledging inflight clientId <{}> packetId {}", clientId, packetId);
        RMap<Integer, String> m = getInflightIdMapping(clientId);
        if (m == null) {
            LOG.error("Can't find the inFlight record for client <{}>", clientId);
            return;
        }
        String guid = m.remove(packetId);

        //remove from the ids store
        RSet<Integer> inFlightForClient = getInflightPacketId(clientId);
        if (inFlightForClient != null) {
            inFlightForClient.remove(packetId);
        }

        LOG.info("Moving to second phase store");
        RMap<Integer, String> messageIds = getSecondPhase(clientId);
        messageIds.put(packetId, guid);
    }

    @Override
    public String pubrelAcknowledged(String clientId, int packetId) {
        RMap<Integer, String> messageIds = getSecondPhase(clientId);
        return messageIds.remove(packetId);
    }

    @Override
    public String mapToGuid(String clientId, int packetId) {
        ConcurrentMap<Integer, String> packetIdToGuid = getIdMappingMap(clientId);
        return packetIdToGuid.get(packetId);
    }

    @Override
    public Message getInflightMessage(String clientId, int packetId) {
        RMap<Integer, String> clientEntries = getInflightIdMapping(clientId);
        if (clientEntries == null)
            return null;
        String guid = clientEntries.get(packetId);
        LOG.info("inflight packetId {} guid <{}>", packetId, guid);
        if (guid == null)
            return null;
        return messagesStore.getMessageByGuid(clientId, guid);
    }

    /**
     * TopicFilter->Subscription
     *
     * @param clientId
     * @return
     */
    private RMap<String, Subscription> getSubscriptions(String clientId) {
        RMap<String, Subscription> rMap = (RMap<String, Subscription>) redissonCache.get(KEY_SUBSCRIPTIONS + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap(KEY_SUBSCRIPTIONS + clientId);
        redissonCache.put(KEY_SUBSCRIPTIONS + clientId, rMap);

        return rMap;
    }

    /**
     * packetId
     *
     * @param clientId
     * @return
     */
    private RSet<Integer> getInflightPacketId(String clientId) {
        RSet<Integer> rSet = (RSet<Integer>) redissonCache.get(KEY_INFLIGHT_PACKETID + clientId);

        if (rSet != null) {
            return rSet;
        }

        rSet = redisson.getSet(KEY_INFLIGHT_PACKETID + clientId);
        redissonCache.put(KEY_INFLIGHT_PACKETID + clientId, rSet);

        return rSet;
    }

    /**
     * packetId -> guid
     *
     * @param clientId
     * @return
     */
    private RMap<Integer, String> getInflightIdMapping(String clientId) {
        RMap<Integer, String> rMap = (RMap<Integer, String>) redissonCache.get(KEY_INFLIGHT_PACKETID_GUID + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap(KEY_INFLIGHT_PACKETID_GUID + clientId);
        redissonCache.put(KEY_INFLIGHT_PACKETID_GUID + clientId, rMap);

        return rMap;
    }

    /**
     * guids
     *
     * @param clientId
     * @return
     */
    private RList<String> getSessionQueue(String clientId) {
        RList<String> rList = (RList<String>) redissonCache.get(KEY_SESSION_QUEUE + clientId);

        if (rList != null) {
            return rList;
        }

        rList = redisson.getList(KEY_SESSION_QUEUE + clientId);
        redissonCache.put(KEY_SESSION_QUEUE + clientId, rList);

        return rList;
    }

    /**
     * packetId->guid
     *
     * @param clientId
     * @return
     */
    private RMap<Integer, String> getSecondPhase(String clientId) {
        RMap<Integer, String> rMap = (RMap<Integer, String>) redissonCache.get(KEY_SECOND_PHASE + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap(KEY_SECOND_PHASE + clientId);
        redissonCache.put(KEY_SECOND_PHASE + clientId, rMap);

        return rMap;
    }

    /**
     * packetId->guid
     *
     * @param clientId
     * @return
     */
    private RMap<Integer, String> getIdMappingMap(String clientId) {

        RMap<Integer, String> rMap = (RMap<Integer, String>) redissonCache.get(KEY_ID_MAPPING + clientId);

        if (rMap != null) {
            return rMap;
        }

        rMap = redisson.getMap(KEY_ID_MAPPING + clientId);
        redissonCache.put(KEY_ID_MAPPING + clientId, rMap);

        return rMap;
    }
}
