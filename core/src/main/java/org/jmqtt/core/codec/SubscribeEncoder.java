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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.jmqtt.core.constant.QosType;
import org.jmqtt.core.packet.AbstractPacket;
import org.jmqtt.core.packet.SubscribePacket;
import org.jmqtt.core.util.MqttUtils;

/**
 * @author andrea
 */
class SubscribeEncoder extends DemuxEncoder<SubscribePacket> {

    @Override
    protected void encode(ChannelHandlerContext chc, SubscribePacket message, ByteBuf out) {
        if (message.subscriptions().isEmpty()) {
            throw new IllegalArgumentException("Found a subscribe message with empty topics");
        }

        if (message.getQos() != QosType.LEAST_ONE) {
            throw new IllegalArgumentException("Expected a message with QOS 1, found " + message.getQos());
        }

        ByteBuf variableHeaderBuff = chc.alloc().buffer(4);
        ByteBuf buff = null;
        try {
            variableHeaderBuff.writeShort(message.getPacketId());
            for (SubscribePacket.Couple c : message.subscriptions()) {
                variableHeaderBuff.writeBytes(MqttUtils.encodeString(c.topicFilter));
                variableHeaderBuff.writeByte(c.qos);
            }

            int variableHeaderSize = variableHeaderBuff.readableBytes();
            byte flags = MqttUtils.encodeFlags(message);
            buff = chc.alloc().buffer(2 + variableHeaderSize);

            buff.writeByte(AbstractPacket.SUBSCRIBE << 4 | flags);
            buff.writeBytes(MqttUtils.encodeRemainingLength(variableHeaderSize));
            buff.writeBytes(variableHeaderBuff);

            out.writeBytes(buff);
        } finally {
            variableHeaderBuff.release();
            buff.release();
        }
    }

}
