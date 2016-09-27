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
import org.jmqtt.core.packet.SubAckPacket;
import org.jmqtt.core.util.MqttUtils;

/**
 * @author andrea
 */
class SubAckEncoder extends DemuxEncoder<SubAckPacket> {

    @Override
    protected void encode(ChannelHandlerContext chc, SubAckPacket message, ByteBuf out) {
        if (message.types().isEmpty()) {
            throw new IllegalArgumentException("Found a suback message with empty topics");
        }

        int variableHeaderSize = 2 + message.types().size();
        ByteBuf buff = chc.alloc().buffer(6 + variableHeaderSize);
        try {
            buff.writeByte(AbstractPacket.SUBACK << 4);
            buff.writeBytes(MqttUtils.encodeRemainingLength(variableHeaderSize));
            buff.writeShort(message.getPacketId());
            for (QosType c : message.types()) {
                buff.writeByte(c.byteValue());
            }

            out.writeBytes(buff);
        } finally {
            buff.release();
        }
    }

}
