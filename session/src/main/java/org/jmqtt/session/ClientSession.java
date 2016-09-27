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
package org.jmqtt.session;

import org.jmqtt.core.constant.QosType;
import org.jmqtt.core.packet.AbstractPacket;
import org.jmqtt.core.packet.PublishPacket;
import org.jmqtt.session.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Model a Session like describe on page 25 of MQTT 3.1.1 specification:
 * The Session state in the Server consists of:
 * <ul>
 * <li>The existence of a Session, even if the rest of the Session state is empty.</li>
 * <li>The Client’s subscriptions.</li>
 * <li>QoS 1 and QoS 2 packets which have been sent to the Client, but have not been
 * completely acknowledged.</li>
 * <li>QoS 1 and QoS 2 packets pending transmission to the Client.</li>
 * <li>QoS 2 packets which have been received from the Client, but have not been
 * completely acknowledged.</li>
 * <li>Optionally, QoS 0 packets pending transmission to the Client.</li>
 * </ul>
 *
 * @author andrea
 */
public class ClientSession {

    private final static Logger LOG = LoggerFactory.getLogger(ClientSession.class);

    public final String clientId;

    private final IMessagesStore messagesStore;

    private final ISessionsStore sessionsStore;

    private Set<Subscription> subscriptions = new HashSet<>();

    private SessionStatus sessionStatus;

    private LinkedBlockingDeque<AbstractPacket> queueToPublish = new LinkedBlockingDeque<>(1024);

    public ClientSession(String clientId, IMessagesStore messagesStore, ISessionsStore sessionsStore, SessionStatus sessionStatus) {
        this.clientId = clientId;
        this.messagesStore = messagesStore;
        this.sessionsStore = sessionsStore;
        this.sessionStatus = sessionStatus;
    }

    /**
     * @return the list of packets to be delivered for client related to the session.
     */
    public List<Message> storedMessages() {
        //read all packets from enqueued store
        Collection<String> guids = this.sessionsStore.enqueued(clientId);
        return messagesStore.listMessagesInSession(clientId, guids);
    }

    /**
     * Remove the packets stored in a cleanSession false.
     */
    public void removeEnqueued(String guid) {
        this.sessionsStore.removeEnqueued(this.clientId, guid);
    }

    @Override
    public String toString() {
        return "ClientSession{clientId='" + clientId + '\'' + "}";
    }

    public boolean subscribe(Subscription newSubscription) {
        LOG.info("<{}> subscribed to the topic filter <{}> with QoS {}",
                newSubscription.getClientId(), newSubscription.getTopicFilter(),
                QosType.formatQoS(newSubscription.getRequestedQos()));
        boolean validTopic = SubscriptionsStore.validate(newSubscription.getTopicFilter());
        if (!validTopic) {
            //send SUBACK with 0x80 for this topic filter
            return false;
        }
        ClientTopicCouple matchingCouple = new ClientTopicCouple(this.clientId, newSubscription.getTopicFilter());
        Subscription existingSub = sessionsStore.getSubscription(matchingCouple);
        //update the selected subscriptions if not present or if has a greater qos
        if (existingSub == null || existingSub.getRequestedQos().byteValue() < newSubscription.getRequestedQos().byteValue()) {
            if (existingSub != null) {
                subscriptions.remove(newSubscription);
            }
            subscriptions.add(newSubscription);
            sessionsStore.addNewSubscription(newSubscription);
        }
        return true;
    }

    public void unsubscribe(String topicFilter) {
        sessionsStore.removeSubscription(topicFilter, clientId);
        Set<Subscription> subscriptionsToRemove = new HashSet<>();
        for (Subscription sub : this.subscriptions) {
            if (sub.getTopicFilter().equals(topicFilter)) {
                subscriptionsToRemove.add(sub);
            }
        }
        subscriptions.removeAll(subscriptionsToRemove);
    }

    public void disconnect() {
        if (this.sessionStatus.isCleanSession()) {
            //cleanup topic subscriptions
            cleanSession();
        }

        //deactivate the session
        deactivate();
    }

    public void cleanSession() {
        LOG.info("cleaning old saved subscriptions for client <{}>", this.clientId);
        sessionsStore.wipeSubscriptions(this.clientId);

        //remove also the packets stored of type QoS1/2
        messagesStore.dropMessagesInSession(this.clientId);
    }

    public boolean isCleanSession() {
        return sessionStatus.isCleanSession();
    }

    public void cleanSession(boolean cleanSession) {
        this.sessionStatus.setCleanSession(cleanSession);
        this.sessionsStore.updateStatus(this.clientId, this.sessionStatus);
    }

    public void activate() {
        this.sessionStatus.setActive(true);
        this.sessionsStore.updateStatus(this.clientId, this.sessionStatus);
    }

    public void deactivate() {
        this.sessionStatus.setActive(false);
        this.sessionsStore.updateStatus(this.clientId, this.sessionStatus);
    }

    public boolean isActive() {
        return this.sessionStatus.isActive();
    }

    public synchronized Integer nextPacketId() {
        return sessionsStore.nextPacketId(clientId);
    }

    public void inFlightAcknowledged(int packetId) {
        LOG.trace("Acknowledging inflight, clientId <{}> packetId {}", this.clientId, packetId);
        sessionsStore.inFlightAck(this.clientId, packetId);
    }

    public void inFlightAckWaiting(String guid, int messageID) {
        LOG.trace("Adding to inflight {}, guid <{}>", messageID, guid);
        sessionsStore.inFlight(this.clientId, messageID, guid);
    }

    public Message secondPhaseAcknowledged(int packetId) {
        String guid = sessionsStore.pubrelAcknowledged(clientId, packetId);
        return messagesStore.getMessageByGuid(clientId, guid);
    }

    public void enqueueToDeliver(String guid) {
        this.sessionsStore.bindToDeliver(guid, this.clientId);
    }

    public Message storedMessage(int packetId) {
        final String guid = sessionsStore.mapToGuid(clientId, packetId);
        return messagesStore.getMessageByGuid(clientId, guid);
    }

    public Message cacheExactlyMessage(int packetId) {
        final String guid = sessionsStore.mapToGuid(clientId, packetId);

        if (guid == null) {
            return null;
        }

        return messagesStore.getCacheMessageByGuid(clientId, guid);
    }

    /**
     * Enqueue a message to be sent to the client.
     *
     * @return false if the queue is full.
     */
    public boolean enqueue(PublishPacket pubMessage) {
        return queueToPublish.offer(pubMessage);
    }

    public AbstractPacket dequeue() {
        return queueToPublish.poll();
    }

    public void pubrelWaiting(int packetId) {
        sessionsStore.pubrelWaiting(this.clientId, packetId);
    }

    public Message getInflightMessage(int packetId) {
        return sessionsStore.getInflightMessage(clientId, packetId);
    }

    public void removeCacheExactlyMessage(Message message) {
        messagesStore.removeCacheExactlyMessage(message);
    }

    public void removeStoredMessage(Message inflightMsg) {
        messagesStore.removeStoredMessage(inflightMsg);
    }
}