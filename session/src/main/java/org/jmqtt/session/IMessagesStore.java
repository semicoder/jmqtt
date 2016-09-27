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


import org.jmqtt.session.model.Message;

import java.util.Collection;
import java.util.List;

/**
 * Defines the SPI to be implemented by a StorageService that handle session of packets
 */
public interface IMessagesStore {

    /**
     * Used to initialize all persistent store structures
     */
    void initStore();

    /**
     * Persist the message.
     * If the message is empty then the topic is cleaned, else it's stored.
     */
    void storeRetained(String topic, Message message);

    /**
     * Return a list of retained packets that satisfy the condition.
     */
    Collection<Message> searchMatching(IMatchingCondition condition);

    /**
     * Persist the message.
     *
     * @return the unique id in the storage (msgId).
     */
    void storePublishForFuture(Message evt);

    void removeStoredMessage(Message inflightMsg);

    void cacheForExactly(Message evt);

    void removeCacheExactlyMessage(Message message);

    /**
     * Return the list of persisted publishes for the given clientId.
     * For QoS1 and QoS2 with clean session flag, this method return the list of
     * missed publish events while the client was disconnected.
     */
    List<Message> listMessagesInSession(String clientId, Collection<String> guids);

    void dropMessagesInSession(String clientID);

    Message getMessageByGuid(String clientId, String guid);

    Message getCacheMessageByGuid(String clientId, String guid);

    void cleanRetained(String topic);
}
