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


import org.jmqtt.session.model.ClientTopicCouple;
import org.jmqtt.session.model.Message;
import org.jmqtt.session.model.SessionStatus;
import org.jmqtt.session.model.Subscription;

import java.util.Collection;
import java.util.List;

/**
 * Store used to handle the session of the subscriptions tree.
 *
 * @author andrea
 */
public interface ISessionsStore {

    void initStore();

    void updateStatus(String clientId, SessionStatus sessionStatus);

    /**
     * Add a new subscription to the session
     */
    void addNewSubscription(Subscription newSubscription);

    /**
     * Removed a specific subscription
     */
    void removeSubscription(String topic, String clientId);

    /**
     * Remove all the subscriptions of the session
     */
    void wipeSubscriptions(String sessionId);

    /**
     * Return all topic filters to recreate the subscription tree.
     */
    List<ClientTopicCouple> listAllSubscriptions();

    /**
     * @return the subscription stored by clientId and topicFilter, if any else null;
     */
    Subscription getSubscription(ClientTopicCouple couple);

    /**
     * @return true if there are subscriptions persisted with clientId
     */
    boolean contains(String clientId);

    ClientSession createNewSession(String clientId, boolean cleanSession);

    /**
     * @param clientId the client owning the session.
     * @return the session for the given clientId, null if not found.
     */
    ClientSession sessionForClient(String clientId);

    void inFlightAck(String clientId, int packetId);

    /**
     * Save the binding packetId, clientId <-> guid
     */
    void inFlight(String clientId, int packetId, String guid);

    /**
     * Return the next valid packetIdentifier for the given client session.
     */
    int nextPacketId(String clientId);

    /**
     * Store the guid to be later published.
     */
    void bindToDeliver(String guid, String clientId);

    /**
     * List the guids for retained packets for the session
     */
    Collection<String> enqueued(String clientId);

    /**
     * Remove form the queue of stored packets for session.
     */
    void removeEnqueued(String clientId, String guid);

    void pubrelWaiting(String clientId, int packetId);

    /**
     * @return the guid of message just acked.
     */
    String pubrelAcknowledged(String clientId, int packetId);

    String mapToGuid(String clientId, int packetId);

    Message getInflightMessage(String clientId, int packetId);

}
