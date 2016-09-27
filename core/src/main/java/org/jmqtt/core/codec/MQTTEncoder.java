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
package org.jmqtt.core.codec;

import org.jmqtt.core.packet.AbstractPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * @author andrea
 */
public class MQTTEncoder extends MessageToByteEncoder<AbstractPacket> {

    private Map<Byte, DemuxEncoder> m_encoderMap = new HashMap<Byte, DemuxEncoder>();

    public MQTTEncoder() {
        m_encoderMap.put(AbstractPacket.CONNECT, new ConnectEncoder());
        m_encoderMap.put(AbstractPacket.CONNACK, new ConnAckEncoder());
        m_encoderMap.put(AbstractPacket.PUBLISH, new PublishEncoder());
        m_encoderMap.put(AbstractPacket.PUBACK, new PubAckEncoder());
        m_encoderMap.put(AbstractPacket.SUBSCRIBE, new SubscribeEncoder());
        m_encoderMap.put(AbstractPacket.SUBACK, new SubAckEncoder());
        m_encoderMap.put(AbstractPacket.UNSUBSCRIBE, new UnsubscribeEncoder());
        m_encoderMap.put(AbstractPacket.DISCONNECT, new DisconnectEncoder());
        m_encoderMap.put(AbstractPacket.PINGREQ, new PingReqEncoder());
        m_encoderMap.put(AbstractPacket.PINGRESP, new PingRespEncoder());
        m_encoderMap.put(AbstractPacket.UNSUBACK, new UnsubAckEncoder());
        m_encoderMap.put(AbstractPacket.PUBCOMP, new PubCompEncoder());
        m_encoderMap.put(AbstractPacket.PUBREC, new PubRecEncoder());
        m_encoderMap.put(AbstractPacket.PUBREL, new PubRelEncoder());
    }

    @Override
    protected void encode(ChannelHandlerContext chc, AbstractPacket msg, ByteBuf bb) throws Exception {
        DemuxEncoder encoder = m_encoderMap.get(msg.getMessageType());
        if (encoder == null) {
            throw new CorruptedFrameException("Can't find any suitable decoder for message type: " + msg.getMessageType());
        }
        encoder.encode(chc, msg, bb);
    }
}
