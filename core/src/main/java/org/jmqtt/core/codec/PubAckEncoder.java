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
import org.jmqtt.core.packet.AbstractPacket;
import org.jmqtt.core.packet.PubAckPacket;
import org.jmqtt.core.util.MqttUtils;

/**
 * @author andrea
 */
class PubAckEncoder extends DemuxEncoder<PubAckPacket> {

    @Override
    protected void encode(ChannelHandlerContext chc, PubAckPacket msg, ByteBuf out) {
        ByteBuf buff = chc.alloc().buffer(4);
        try {
            buff.writeByte(AbstractPacket.PUBACK << 4);
            buff.writeBytes(MqttUtils.encodeRemainingLength(2));
            buff.writeShort(msg.getPacketId());
            out.writeBytes(buff);
        } finally {
            buff.release();
        }
    }

}
