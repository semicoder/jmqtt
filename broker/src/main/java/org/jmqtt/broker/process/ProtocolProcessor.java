/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.jmqtt.broker.process;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleStateHandler;
import org.jmqtt.auth.IAuthenticator;
import org.jmqtt.auth.IAuthorizator;
import org.jmqtt.broker.ConnectionDescriptor;
import org.jmqtt.broker.config.AcceptorProperties;
import org.jmqtt.broker.handler.AutoFlushHandler;
import org.jmqtt.core.constant.QosType;
import org.jmqtt.interception.BrokerInterceptor;
import org.jmqtt.broker.util.DebugUtils;
import org.jmqtt.broker.util.NettyUtils;
import org.jmqtt.core.packet.*;
import org.jmqtt.interception.InterceptHandler;
import org.jmqtt.interception.messages.InterceptAcknowledgedMessage;
import org.jmqtt.session.ClientSession;
import org.jmqtt.session.IMatchingCondition;
import org.jmqtt.session.IMessagesStore;
import org.jmqtt.session.ISessionsStore;
import org.jmqtt.session.model.Message;
import org.jmqtt.session.model.Subscription;
import org.jmqtt.session.model.SubscriptionsStore;
import org.jmqtt.session.model.WillMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.jmqtt.core.util.MqttUtils.VERSION_3_1;
import static org.jmqtt.core.util.MqttUtils.VERSION_3_1_1;

/**
 * Class responsible to handle the logic of MQTT protocol it's the director of
 * the protocol execution.
 * <p>
 * Used by the front facing class ProtocolProcessorBootstrapper.
 *
 * @author andrea
 */
@Component
public class ProtocolProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolProcessor.class);

    protected ConcurrentMap<String, ConnectionDescriptor> clientIds = new ConcurrentHashMap<>();

    private SubscriptionsStore subscriptions;

    @Autowired
    private IAuthorizator authorizator;
    @Autowired
    private IAuthenticator authenticator;
    @Autowired
    private IMessagesStore messagesStore;
    @Autowired
    private ISessionsStore sessionsStore;

    @Autowired
    private AcceptorProperties props;

    @Autowired
    private BrokerInterceptor interceptor;

    //maps clientId to Will testament, if specified on CONNECT
    private ConcurrentMap<String, WillMessage> willStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        subscriptions = new SubscriptionsStore();
        subscriptions.init(sessionsStore);
    }

    public void processConnect(Channel channel, ConnectPacket packet) {
        LOG.debug("CONNECT for client <{}>", packet.getClientId());
        if (packet.getProtocolVersion() != VERSION_3_1 && packet.getProtocolVersion() != VERSION_3_1_1) {
            ConnAckPacket badProto = new ConnAckPacket();
            badProto.setReturnCode(ConnAckPacket.UNNACEPTABLE_PROTOCOL_VERSION);
            LOG.warn("processConnect sent bad proto ConnAck");
            channel.writeAndFlush(badProto);
            channel.close();
            return;
        }

        if (packet.getClientId() == null || packet.getClientId().length() == 0) {
            if (!packet.isCleanSession() || !props.getAllowZeroByteClientId()) {
                ConnAckPacket okResp = new ConnAckPacket();
                okResp.setReturnCode(ConnAckPacket.IDENTIFIER_REJECTED);
                channel.writeAndFlush(okResp);
                channel.close();
                return;
            }

            // Generating client id.
            String randomIdentifier = UUID.randomUUID().toString().replace("-", "");
            packet.setClientID(randomIdentifier);
            LOG.info("Client connected with server generated identifier: {}", randomIdentifier);
        }

        //handle user authentication
        if (packet.isUserFlag()) {
            byte[] pwd = null;
            if (packet.isPasswordFlag()) {
                pwd = packet.getPassword();
            } else if (!props.getAllowAnonymous()) {
                failedCredentials(channel);
                return;
            }
            if (!authenticator.checkValid(packet.getClientId(), packet.getUsername(), pwd, props.getAllowZeroByteClientId())) {
                failedCredentials(channel);
                channel.close();
                return;
            }
            NettyUtils.userName(channel, packet.getUsername());
        } else if (!props.getAllowAnonymous()) {
            failedCredentials(channel);
            return;
        }

        //if an old client with the same ID already exists close its session.
        if (clientIds.containsKey(packet.getClientId())) {
            LOG.info("Found an existing connection with same client ID <{}>, forcing to close", packet.getClientId());
            //clean the subscriptions if the old used a cleanSession = true
            Channel oldChannel = clientIds.get(packet.getClientId()).channel;
            ClientSession oldClientSession = sessionsStore.sessionForClient(packet.getClientId());
            oldClientSession.disconnect();
            NettyUtils.sessionStolen(oldChannel, true);
            oldChannel.close();
            LOG.debug("Existing connection with same client ID <{}>, forced to close", packet.getClientId());
        }

        ConnectionDescriptor connDescr = new ConnectionDescriptor(packet.getClientId(), channel, packet.isCleanSession());
        clientIds.put(packet.getClientId(), connDescr);

        int keepAlive = packet.getKeepAlive();
        LOG.debug("Connect with keepAlive {} s", keepAlive);
        NettyUtils.keepAlive(channel, keepAlive);
        //session.attr(NettyUtils.ATTR_KEY_CLEANSESSION).set(msg.isCleanSession());
        NettyUtils.cleanSession(channel, packet.isCleanSession());
        //used to track the client in the subscription and publishing phases.
        //session.attr(NettyUtils.ATTR_KEY_CLIENTID).set(msg.getClientId());
        NettyUtils.clientId(channel, packet.getClientId());
        LOG.debug("Connect create session <{}>", channel);

        setIdleTime(channel.pipeline(), Math.round(keepAlive * 1.5f));

        //Handle will flag
        if (packet.isWillFlag()) {
            QosType willQos = QosType.valueOf(packet.getWillQos());
            byte[] willPayload = packet.getWillMessage();
            ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(willPayload.length).put(willPayload).flip();
            //save the will testament in the clientId store
            WillMessage will = new WillMessage(packet.getWillTopic(), bb, packet.isWillRetain(), willQos);
            willStore.put(packet.getClientId(), will);
            LOG.info("Session for clientId <{}> with will to topic {}", packet.getClientId(), packet.getWillTopic());
        }

        ConnAckPacket okResp = new ConnAckPacket();
        okResp.setReturnCode(ConnAckPacket.CONNECTION_ACCEPTED);

        ClientSession clientSession = sessionsStore.sessionForClient(packet.getClientId());
        boolean isSessionAlreadyStored = clientSession != null;
        if (!packet.isCleanSession() && isSessionAlreadyStored) {
            okResp.setSessionPresent(true);
        }
        if (isSessionAlreadyStored) {
            clientSession.cleanSession(packet.isCleanSession());
        }
        channel.writeAndFlush(okResp);
        interceptor.notifyClientConnected(packet);

        if (!isSessionAlreadyStored) {
            LOG.info("Create persistent session for clientId <{}>", packet.getClientId());
            clientSession = sessionsStore.createNewSession(packet.getClientId(), packet.isCleanSession());
        }
        clientSession.activate();
        if (packet.isCleanSession()) {
            clientSession.cleanSession();
        }
        LOG.info("Connected client ID <{}> with clean session {}", packet.getClientId(), packet.isCleanSession());
        if (!packet.isCleanSession()) {
            //force the republish of stored QoS1 and QoS2
            republishStoredInSession(clientSession);
        }
        int flushIntervalMs = 500/*(keepAlive * 1000) / 2*/;
        setupAutoFlusher(channel.pipeline(), flushIntervalMs);
        LOG.info("CONNECT processed");
    }

    private void setupAutoFlusher(ChannelPipeline pipeline, int flushIntervalMs) {
        AutoFlushHandler autoFlushHandler = new AutoFlushHandler(flushIntervalMs, TimeUnit.MILLISECONDS);
        try {
            pipeline.addAfter("idleEventHandler", "autoFlusher", autoFlushHandler);
        } catch (NoSuchElementException nseex) {
            //the idleEventHandler is not present on the pipeline
            pipeline.addFirst("autoFlusher", autoFlushHandler);
        }
    }

    private void setIdleTime(ChannelPipeline pipeline, int idleTime) {
        if (pipeline.names().contains("idleStateHandler")) {
            pipeline.remove("idleStateHandler");
        }
        pipeline.addFirst("idleStateHandler", new IdleStateHandler(0, 0, idleTime));
    }

    private void failedCredentials(Channel session) {
        ConnAckPacket okResp = new ConnAckPacket();
        okResp.setReturnCode(ConnAckPacket.BAD_USERNAME_OR_PASSWORD);
        session.writeAndFlush(okResp);
        session.close();
        LOG.info("Client {} failed to connect with bad username or password.", session);
    }

    /**
     * Republish QoS1 and QoS2 packets stored into the session for the clientId.
     */
    private void republishStoredInSession(ClientSession clientSession) {
        LOG.trace("republishStoredInSession for client <{}>", clientSession);
        List<Message> publishedEvents = clientSession.storedMessages();
        if (publishedEvents.isEmpty()) {
            LOG.info("No stored packets for client <{}>", clientSession.clientId);
            return;
        }

        LOG.info("republishing stored packets to client <{}>", clientSession.clientId);
        for (Message pubEvt : publishedEvents) {
            //put in flight zone
            LOG.trace("Adding to inflight <{}>", pubEvt.getPacketId());
            clientSession.inFlightAckWaiting(pubEvt.getMsgId(), pubEvt.getPacketId());
            directSend(clientSession, pubEvt.getTopic(), pubEvt.getQos(),
                    pubEvt.getMessage(), false, pubEvt.getPacketId());
            clientSession.removeEnqueued(pubEvt.getMsgId());
        }
    }

    public void processPubAck(Channel channel, PubAckPacket packet) {
        String clientId = NettyUtils.clientId(channel);

        if (clientId == null) {
            channel.close();
            return;
        }

        int packetId = packet.getPacketId();
        String username = NettyUtils.userName(channel);
        LOG.trace("retrieving inflight for packetId <{}>", packetId);

        //Remove the message from message store
        ClientSession targetSession = sessionsStore.sessionForClient(clientId);
        verifyToActivate(targetSession);
        Message inflightMsg = targetSession.getInflightMessage(packetId);
        targetSession.inFlightAcknowledged(packetId);

        String topic = inflightMsg.getTopic();

        interceptor.notifyMessageAcknowledged(new InterceptAcknowledgedMessage(inflightMsg, topic, username));
    }

    private void verifyToActivate(ClientSession targetSession) {
        if (targetSession == null) {
            return;
        }
        String clientId = targetSession.clientId;
        if (clientIds.containsKey(clientId)) {
            targetSession.activate();
        }
    }

    private static Message asStoredMessage(PublishPacket msg) {
        Message stored = new Message(msg.getPayload().array(), msg.getQos(), msg.getTopicName());
        stored.setRetained(msg.isRetainFlag());
        stored.setPacketId(msg.getPacketId());
        stored.setMsgId(UUID.randomUUID().toString());
        return stored;
    }

    private static Message asStoredMessage(WillMessage will) {
        Message pub = new Message(will.getPayload().array(), will.getQos(), will.getTopic());
        pub.setRetained(will.isRetained());
        return pub;
    }

    public void processPublish(Channel channel, PublishPacket packet) {
        LOG.trace("PUB --PUBLISH--> SRV executePublish invoked with {}", packet);
        String clientId = NettyUtils.clientId(channel);

        if (clientId == null) {
            channel.close();
            return;
        }

        final String topic = packet.getTopicName();
        //check if the topic can be wrote
        String username = NettyUtils.userName(channel);
        if (!authorizator.canWrite(topic, username, clientId)) {
            LOG.debug("topic {} doesn't have write credentials", topic);
            return;
        }
        final QosType qos = packet.getQos();
        final Integer packetId = packet.getPacketId();
        LOG.info("PUBLISH from clientId <{}> on topic <{}> with QoS {}", clientId, topic, qos);

        Message toStoreMsg = asStoredMessage(packet);
        toStoreMsg.setClientId(clientId);
        if (qos == QosType.MOST_ONE) { //QoS0
            route2Subscribers(toStoreMsg);
        } else if (qos == QosType.LEAST_ONE) { //QoS1
            route2Subscribers(toStoreMsg);
            if (packet.isLocal()) {
                sendPubAck(clientId, packetId);
            }
            LOG.info("replying with PubAck to MSG ID {}", packetId);
        } else if (qos == QosType.EXACTLY_ONCE) { //QoS2
            messagesStore.cacheForExactly(toStoreMsg);

            if (packet.isLocal()) {
                sendPubRec(clientId, packetId);
            }
            //Next the client will send us a pub rel
            //NB publish to subscribers for QoS 2 happen upon PUBREL from publisher
        }

        if (packet.isRetainFlag()) {
            if (qos == QosType.MOST_ONE) {
                //QoS == 0 && retain => clean old retained
                messagesStore.cleanRetained(topic);
            } else {
                if (!packet.getPayload().hasRemaining()) {
                    messagesStore.cleanRetained(topic);
                } else {
                    messagesStore.storeRetained(topic, toStoreMsg);
                }
            }
        }
        interceptor.notifyTopicPublished(packet, clientId, username);
    }

    /**
     * Intended usage is only for embedded versions of the broker, where the hosting application want to use the
     * broker to send a publish message.
     * Inspired by {@link #processPublish} but with some changes to avoid security check, and the handshake phases
     * for Qos1 and Qos2.
     * It also doesn't notifyTopicPublished because using internally the owner should already know where
     * it's publishing.
     */
    public void internalPublish(PublishPacket packet) {
        final QosType qos = packet.getQos();
        final String topic = packet.getTopicName();
        LOG.info("embedded PUBLISH on topic <{}> with QoS {}", topic, qos);

        Message toStoreMsg = asStoredMessage(packet);
        if (packet.getClientId() == null || packet.getClientId().isEmpty()) {
            toStoreMsg.setClientId("BROKER_SELF");
        } else {
            toStoreMsg.setClientId(packet.getClientId());
        }
        toStoreMsg.setPacketId(1);
        route2Subscribers(toStoreMsg);

        if (!packet.isRetainFlag()) {
            return;
        }
        if (qos == QosType.MOST_ONE || !packet.getPayload().hasRemaining()) {
            //QoS == 0 && retain => clean old retained
            messagesStore.cleanRetained(topic);
            return;
        }
        messagesStore.storeRetained(topic, toStoreMsg);
    }

    /**
     * Specialized version to publish will testament message.
     */
    private void forwardPublishWill(WillMessage will, String clientId) {
        //it has just to publish the message downstream to the subscribers
        //NB it's a will publish, it needs a PacketIdentifier for this conn, default to 1
        Integer packetId = null;
        if (will.getQos() != QosType.MOST_ONE) {
            packetId = sessionsStore.nextPacketId(clientId);
        }

        Message tobeStored = asStoredMessage(will);
        tobeStored.setClientId(clientId);
        tobeStored.setPacketId(packetId);
        route2Subscribers(tobeStored);
    }


    /**
     * Flood the subscribers with the message to notify. packetId is optional and should only used for QoS 1 and 2
     */
    void route2Subscribers(Message pubMsg) {
        final String topic = pubMsg.getTopic();
        final QosType publishingQos = pubMsg.getQos();
        final ByteBuffer origMessage = pubMsg.getMessage();
        LOG.debug("route2Subscribers republishing to existing subscribers that matches the topic {}", topic);
        if (LOG.isTraceEnabled()) {
            LOG.trace("content <{}>", DebugUtils.payload2Str(origMessage));
            LOG.trace("subscription tree {}", subscriptions.dumpTree());
        }

        List<Subscription> topicMatchingSubscriptions = subscriptions.matches(topic);
        LOG.trace("Found {} matching subscriptions to <{}>", topicMatchingSubscriptions.size(), topic);
        for (final Subscription sub : topicMatchingSubscriptions) {
            QosType qos = publishingQos;
            if (qos.byteValue() > sub.getRequestedQos().byteValue()) {
                qos = sub.getRequestedQos();
            }
            ClientSession targetSession = sessionsStore.sessionForClient(sub.getClientId());
            verifyToActivate(targetSession);

            LOG.debug("Broker republishing to client <{}> topic <{}> qos <{}>, active {}",
                    sub.getClientId(), sub.getTopicFilter(), qos, targetSession.isActive());
            ByteBuffer message = origMessage.duplicate();
            if (qos == QosType.MOST_ONE && targetSession.isActive() && clientIds.containsKey(targetSession.clientId)) {
                //QoS 0
                directSend(targetSession, topic, qos, message, false, null);
            } else {
                Message storedMessage = new Message();
                storedMessage.setMsgId(pubMsg.getMsgId());
                storedMessage.setPacketId(pubMsg.getPacketId());
                storedMessage.setPayload(pubMsg.getPayload());
                storedMessage.setQos(pubMsg.getQos());
                storedMessage.setRetained(pubMsg.isRetained());
                storedMessage.setTopic(pubMsg.getTopic());

                storedMessage.setClientId(sub.getClientId());

                messagesStore.storePublishForFuture(storedMessage);
                //QoS 1 or 2
                //if the target subscription is not clean session and is not connected => store it
                if (!targetSession.isCleanSession() && !targetSession.isActive()) {
                    //store the message in targetSession queue to deliver
                    targetSession.enqueueToDeliver(pubMsg.getMsgId());
                } else {
                    //publish
                    if (targetSession.isActive() && clientIds.containsKey(targetSession.clientId)) {
                        int packetId = targetSession.nextPacketId();
                        targetSession.inFlightAckWaiting(pubMsg.getMsgId(), packetId);
                        directSend(targetSession, topic, qos, message, false, packetId);
                    }
                }
            }
        }
    }

    protected void directSend(ClientSession clientsession, String topic, QosType qos,
                              ByteBuffer message, boolean retained, Integer packetId) {
        String clientId = clientsession.clientId;
        LOG.debug("directSend invoked clientId <{}> on topic <{}> QoS {} retained {} packetId {}",
                clientId, topic, qos, retained, packetId);
        PublishPacket pubPacket = new PublishPacket();
        pubPacket.setRetainFlag(retained);
        pubPacket.setTopicName(topic);
        pubPacket.setQos(qos);
        pubPacket.setPayload(message);

        LOG.info("send publish message to <{}> on topic <{}>", clientId, topic);
        if (LOG.isDebugEnabled()) {
            LOG.debug("content <{}>", DebugUtils.payload2Str(message));
        }
        //set the PacketIdentifier only for QoS > 0
        if (pubPacket.getQos() != QosType.MOST_ONE) {
            pubPacket.setPacketId(packetId);
        } else {
            if (packetId != null) {
                throw new RuntimeException("Internal bad error, trying to forwardPublish a QoS 0 message " +
                        "with PacketIdentifier: " + packetId);
            }
        }

        if (clientIds == null) {
            throw new RuntimeException("Internal bad error, found clientIds to null while it should be " +
                    "initialized, somewhere it's overwritten!!");
        }
        if (clientIds.get(clientId) == null) {
            //TODO while we were publishing to the target client, that client disconnected,
            // could happen is not an error HANDLE IT
            throw new RuntimeException(String.format("Can't find a ConnectionDescriptor for client <%s> in cache <%s>",
                    clientId, clientIds));
        }
        Channel channel = clientIds.get(clientId).channel;
        LOG.trace("Session for clientId {}", clientId);
        if (channel.isWritable()) {
            //if channel is writable don't enqueue
            channel.write(pubPacket);
        } else {
            //enqueue to the client session
            clientsession.enqueue(pubPacket);
        }
    }

    private void sendPubRec(String clientId, int packetId) {
        LOG.trace("PUB <--PUBREC-- SRV sendPubRec invoked for clientId {} with packetId {}", clientId, packetId);
        PubRecPacket pubRecPacket = new PubRecPacket();
        pubRecPacket.setPacketId(packetId);
        clientIds.get(clientId).channel.writeAndFlush(pubRecPacket);
    }

    private void sendPubAck(String clientId, int packetId) {
        LOG.trace("sendPubAck invoked");
        PubAckPacket pubAckPacket = new PubAckPacket();
        pubAckPacket.setPacketId(packetId);

        try {
            if (clientIds == null) {
                throw new RuntimeException("Internal bad error, found clientIds to null while it should be initialized, somewhere it's overwritten!!");
            }
            LOG.debug("clientIds are {}", clientIds);
            if (clientIds.get(clientId) == null) {
                throw new RuntimeException(String.format("Can't find a ConnectionDescriptor for client %s in cache %s", clientId, clientIds));
            }
            clientIds.get(clientId).channel.writeAndFlush(pubAckPacket);
        } catch (Throwable t) {
            LOG.error(null, t);
        }
    }

    /**
     * Second phase of a publish QoS2 protocol, sent by publisher to the broker. Search the stored message and publish
     * to all interested subscribers.
     */
    public void processPubRel(Channel channel, PubRelPacket packet) {
        String clientId = NettyUtils.clientId(channel);
        int packetId = packet.getPacketId();
        LOG.debug("PUB --PUBREL--> SRV processPubRel invoked for clientId {} ad packetId {}", clientId, packetId);
        ClientSession targetSession = sessionsStore.sessionForClient(clientId);
        verifyToActivate(targetSession);
        Message message = targetSession.cacheExactlyMessage(packetId);

        if (message != null) {
            route2Subscribers(message);

            if (message.isRetained()) {
                final String topic = message.getTopic();
                if (!message.getMessage().hasRemaining()) {
                    messagesStore.cleanRetained(topic);
                } else {
                    messagesStore.storeRetained(topic, message);
                }
            }

            targetSession.removeCacheExactlyMessage(message);
        }

        sendPubComp(clientId, packetId);
    }

    private void sendPubComp(String clientId, int packetId) {
        LOG.debug("PUB <--PUBCOMP-- SRV sendPubComp invoked for clientId {} ad packetId {}", clientId, packetId);
        PubCompPacket pubCompMessage = new PubCompPacket();
        pubCompMessage.setPacketId(packetId);

        clientIds.get(clientId).channel.writeAndFlush(pubCompMessage);
    }

    public void processPubRec(Channel channel, PubRecPacket msg) {
        String clientID = NettyUtils.clientId(channel);

        if (clientID == null) {
            channel.close();
            return;
        }

        ClientSession targetSession = sessionsStore.sessionForClient(clientID);
        verifyToActivate(targetSession);
        //remove from the inflight and move to the QoS2 second phase queue
        int packetId = msg.getPacketId();
        targetSession.pubrelWaiting(packetId);
        //once received a PUBREC reply with a PUBREL(packetId)
        LOG.debug("\t\tSRV <--PUBREC-- SUB processPubRec invoked for clientId {} ad packetId {}", clientID, packetId);
        PubRelPacket pubRelMessage = new PubRelPacket();
        pubRelMessage.setPacketId(packetId);

        channel.writeAndFlush(pubRelMessage);
    }

    public void processPubComp(Channel channel, PubCompPacket msg) {
        String clientId = NettyUtils.clientId(channel);
        int packetId = msg.getPacketId();
        LOG.debug("\t\tSRV <--PUBCOMP-- SUB processPubComp invoked for clientId {} ad packetId {}", clientId, packetId);
        //once received the PUBCOMP then remove the message from the temp memory
        ClientSession targetSession = sessionsStore.sessionForClient(clientId);
        verifyToActivate(targetSession);
        Message inflightMsg = targetSession.secondPhaseAcknowledged(packetId);

        targetSession.removeStoredMessage(inflightMsg);

        String username = NettyUtils.userName(channel);
        String topic = inflightMsg.getTopic();
        interceptor.notifyMessageAcknowledged(new InterceptAcknowledgedMessage(inflightMsg, topic, username));
    }

    public void processDisconnect(Channel channel) throws InterruptedException {
        channel.flush();
        String clientID = NettyUtils.clientId(channel);
        boolean cleanSession = NettyUtils.cleanSession(channel);
        LOG.info("DISCONNECT client <{}> with clean session {}", clientID, cleanSession);
        ClientSession clientSession = sessionsStore.sessionForClient(clientID);
        clientSession.disconnect();

        clientIds.remove(clientID);
        channel.close();

        //cleanup the will store
        willStore.remove(clientID);

        String username = NettyUtils.userName(channel);
        interceptor.notifyClientDisconnected(clientID, username);
        LOG.info("DISCONNECT client <{}> finished", clientID, cleanSession);
    }

    public void processConnectionLost(String clientId, boolean sessionStolen, Channel channel) {
        ConnectionDescriptor oldConnDescr = new ConnectionDescriptor(clientId, channel, true);
        clientIds.remove(clientId, oldConnDescr);
        //If already removed a disconnect message was already processed for this clientId
        if (sessionStolen) {
            //de-activate the subscriptions for this ClientID
            ClientSession clientSession = sessionsStore.sessionForClient(clientId);
            if (clientSession.isCleanSession())
            clientSession.deactivate();
            LOG.info("Lost connection with client <{}>", clientId);
        }
        //publish the Will message (if any) for the clientId
        if (!sessionStolen && willStore.containsKey(clientId)) {
            WillMessage will = willStore.get(clientId);
            forwardPublishWill(will, clientId);
            willStore.remove(clientId);
        }
    }

    /**
     * Remove the clientId from topic subscription, if not previously subscribed,
     * doesn't reply any error
     */
    public void processUnsubscribe(Channel channel, UnsubscribePacket msg) {
        List<String> topics = msg.topicFilters();
        int messageID = msg.getPacketId();
        String clientID = NettyUtils.clientId(channel);

        LOG.debug("UNSUBSCRIBE subscription on topics {} for clientId <{}>", topics, clientID);

        ClientSession clientSession = sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientSession);
        for (String topic : topics) {
            boolean validTopic = SubscriptionsStore.validate(topic);
            if (!validTopic) {
                //close the connection, not valid topicFilter is a protocol violation
                channel.close();
                LOG.warn("UNSUBSCRIBE found an invalid topic filter <{}> for clientId <{}>", topic, clientID);
                return;
            }

            subscriptions.removeSubscription(topic, clientID);
            clientSession.unsubscribe(topic);
            String username = NettyUtils.userName(channel);
            interceptor.notifyTopicUnsubscribed(topic, clientID, username);
        }

        //ack the client
        UnsubAckPacket ackMessage = new UnsubAckPacket();
        ackMessage.setPacketId(messageID);

        LOG.info("replying with UnsubAck to MSG ID {}", messageID);
        channel.writeAndFlush(ackMessage);
    }

    public void processSubscribe(Channel channel, SubscribePacket msg) {
        String clientId = NettyUtils.clientId(channel);
        LOG.debug("SUBSCRIBE client <{}> packetID {}", clientId, msg.getPacketId());

        ClientSession clientSession = sessionsStore.sessionForClient(clientId);
        verifyToActivate(clientSession);
        //ack the client
        SubAckPacket ackMessage = new SubAckPacket();
        ackMessage.setPacketId(msg.getPacketId());

        String username = NettyUtils.userName(channel);
        List<Subscription> newSubscriptions = new ArrayList<>();
        for (SubscribePacket.Couple req : msg.subscriptions()) {
            if (!authorizator.canRead(req.topicFilter, username, clientSession.clientId)) {
                //send SUBACK with 0x80, the user hasn't credentials to read the topic
                LOG.debug("topic {} doesn't have read credentials", req.topicFilter);
                ackMessage.addType(QosType.FAILURE);
                continue;
            }

            QosType qos = QosType.valueOf(req.qos);
            Subscription newSubscription = new Subscription(clientId, req.topicFilter, qos);
            boolean valid = clientSession.subscribe(newSubscription);
            ackMessage.addType(valid ? qos : QosType.FAILURE);
            if (valid) {
                newSubscriptions.add(newSubscription);
            }
        }

        //save session, persist subscriptions from session
        LOG.debug("SUBACK for packetID {}", msg.getPacketId());
        if (LOG.isTraceEnabled()) {
            LOG.trace("subscription tree {}", subscriptions.dumpTree());
        }

        for (Subscription subscription : newSubscriptions) {
            subscribeSingleTopic(subscription);
        }
        channel.writeAndFlush(ackMessage);

        //fire the persisted packets in session
        for (Subscription subscription : newSubscriptions) {
            publishStoredMessagesInSession(subscription, username);
        }
    }

    private void subscribeSingleTopic(final Subscription newSubscription) {
        LOG.debug("Subscribing {}", newSubscription);
        subscriptions.add(newSubscription.asClientTopicCouple());
    }

    private void publishStoredMessagesInSession(final Subscription newSubscription, String username) {
        LOG.debug("Publish persisted packets in session {}", newSubscription);

        //scans retained packets to be published to the new subscription
        //TODO this is ugly, it does a linear scan on potential big dataset
        Collection<Message> messages = messagesStore.searchMatching(new IMatchingCondition() {
            @Override
            public boolean match(String key) {
                return SubscriptionsStore.matchTopics(key, newSubscription.getTopicFilter());
            }
        });

        LOG.debug("Found {} packets to republish", messages.size());
        ClientSession targetSession = sessionsStore.sessionForClient(newSubscription.getClientId());
        verifyToActivate(targetSession);
        for (Message storedMsg : messages) {
            //fire as retained the message
            LOG.trace("send publish message for topic {}", newSubscription.getTopicFilter());
            Integer packetId = storedMsg.getQos() == QosType.MOST_ONE ? null : targetSession.nextPacketId();
            if (packetId != null) {
                LOG.trace("Adding to inflight <{}>", packetId);
                targetSession.inFlightAckWaiting(storedMsg.getMsgId(), packetId);
            }
            directSend(targetSession, storedMsg.getTopic(), storedMsg.getQos(), storedMsg.getPayloadBuffer(), true, packetId);
        }

        //notify the Observables
        interceptor.notifyTopicSubscribed(newSubscription, username);
    }

    public void notifyChannelWritable(Channel channel) {
        String clientId = NettyUtils.clientId(channel);
        ClientSession clientSession = sessionsStore.sessionForClient(clientId);
        boolean emptyQueue = false;
        while (channel.isWritable() && !emptyQueue) {
            AbstractPacket packet = clientSession.dequeue();
            if (packet == null) {
                emptyQueue = true;
            } else {
                channel.write(packet);
            }
        }
        channel.flush();
    }

    public boolean addInterceptHandler(InterceptHandler interceptHandler) {
        return this.interceptor.addInterceptHandler(interceptHandler);
    }

    public boolean removeInterceptHandler(InterceptHandler interceptHandler) {
        return this.interceptor.removeInterceptHandler(interceptHandler);
    }
}
