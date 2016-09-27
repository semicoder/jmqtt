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

/**
 * Base class for alla the packets that carries only MessageID. (PUBACK, PUBREC,
 * PUBREL, PUBCOMP, UNSUBACK)
 * <p>
 * The flags dup, QOS and Retained doesn't take care.
 *
 * @author andrea
 */
public abstract class PacketIdPacket extends AbstractPacket {
    protected Integer packetId; //could be null if Qos is == 0

    public Integer getPacketId() {
        return packetId;
    }

    public void setPacketId(Integer packetId) {
        this.packetId = packetId;
    }

}
