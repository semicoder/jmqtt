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
package org.jmqtt.core.packet;

import org.jmqtt.core.constant.QosType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author andrea
 */
public class SubscribePacket extends PacketIdPacket {

    public static class Couple {

        public final byte qos;
        public final String topicFilter;

        public Couple(byte qos, String topic) {
            this.qos = qos;
            this.topicFilter = topic;
        }

    }

    private List<Couple> subscriptions = new ArrayList<>();

    public SubscribePacket() {
        //Subscribe has always QoS 1
        messageType = AbstractPacket.SUBSCRIBE;
        qos = QosType.LEAST_ONE;
    }

    public List<Couple> subscriptions() {
        return subscriptions;
    }

    public void addSubscription(Couple subscription) {
        subscriptions.add(subscription);
    }
}
