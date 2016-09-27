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
import org.jmqtt.core.packet.PublishPacket;
import org.jmqtt.core.util.MqttUtils;

/**
 * @author andrea
 */
class PublishEncoder extends DemuxEncoder<PublishPacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, PublishPacket message, ByteBuf out) {
        if (message.getQos() == QosType.RESERVED) {
            throw new IllegalArgumentException("Found a message with RESERVED Qos");
        }
        if (message.getTopicName() == null || message.getTopicName().isEmpty()) {
            throw new IllegalArgumentException("Found a message with empty or null topic name");
        }

        ByteBuf variableHeaderBuff = ctx.alloc().buffer(2);
        ByteBuf buff = null;
        try {
            variableHeaderBuff.writeBytes(MqttUtils.encodeString(message.getTopicName()));
            if (message.getQos() == QosType.LEAST_ONE ||
                    message.getQos() == QosType.EXACTLY_ONCE) {
                if (message.getPacketId() == null) {
                    throw new IllegalArgumentException("Found a message with QOS 1 or 2 and not MessageID setted");
                }
                variableHeaderBuff.writeShort(message.getPacketId());
            }
            variableHeaderBuff.writeBytes(message.getPayload());
            int variableHeaderSize = variableHeaderBuff.readableBytes();

            byte flags = MqttUtils.encodeFlags(message);

            buff = ctx.alloc().buffer(2 + variableHeaderSize);
            buff.writeByte(AbstractPacket.PUBLISH << 4 | flags);
            buff.writeBytes(MqttUtils.encodeRemainingLength(variableHeaderSize));
            buff.writeBytes(variableHeaderBuff);
            out.writeBytes(buff);
        } finally {
            variableHeaderBuff.release();
            if (buff != null) {
                buff.release();
            }
        }
    }

}
