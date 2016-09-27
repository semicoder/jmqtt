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
package org.jmqtt.interception.messages;


import org.jmqtt.core.constant.QosType;
import org.jmqtt.core.packet.AbstractPacket;

/**
 * @author Wagner Macedo
 */
public abstract class InterceptAbstractMessage {
    private final AbstractPacket msg;

    InterceptAbstractMessage(AbstractPacket msg) {
        this.msg = msg;
    }

    public boolean isRetainFlag() {
        return msg.isRetainFlag();
    }

    public boolean isDupFlag() {
        return msg.isDupFlag();
    }

    public QosType getQos() {
        return msg.getQos();
    }
}
