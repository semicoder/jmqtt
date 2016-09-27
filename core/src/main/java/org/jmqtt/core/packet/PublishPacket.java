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

import java.nio.ByteBuffer;

/**
 * @author andrea
 */
public class PublishPacket extends PacketIdPacket {

    private boolean local = true;
    private String clientId;

    protected String topicName;
    protected ByteBuffer payload;

    public PublishPacket() {
        messageType = AbstractPacket.PUBLISH;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public void setPayload(ByteBuffer payload) {
        this.payload = payload;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
