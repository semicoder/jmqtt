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
import org.jmqtt.core.packet.ConnectPacket;
import org.jmqtt.core.util.MqttUtils;

/**
 * @author andrea
 */
public class ConnectEncoder extends DemuxEncoder<ConnectPacket> {

    @Override
    protected void encode(ChannelHandlerContext chc, ConnectPacket message, ByteBuf out) {
        ByteBuf staticHeaderBuff = chc.alloc().buffer(12);
        ByteBuf buff = chc.alloc().buffer();
        ByteBuf variableHeaderBuff = chc.alloc().buffer(12);
        try {
            staticHeaderBuff.writeBytes(MqttUtils.encodeString("MQIsdp"));

            //version 
            staticHeaderBuff.writeByte(0x03);

            //connection flags and Strings
            byte connectionFlags = 0;
            if (message.isCleanSession()) {
                connectionFlags |= 0x02;
            }
            if (message.isWillFlag()) {
                connectionFlags |= 0x04;
            }
            connectionFlags |= ((message.getWillQos() & 0x03) << 3);
            if (message.isWillRetain()) {
                connectionFlags |= 0x020;
            }
            if (message.isPasswordFlag()) {
                connectionFlags |= 0x040;
            }
            if (message.isUserFlag()) {
                connectionFlags |= 0x080;
            }
            staticHeaderBuff.writeByte(connectionFlags);

            //Keep alive timer
            staticHeaderBuff.writeShort(message.getKeepAlive());

            //Variable part
            if (message.getClientId() != null) {
                variableHeaderBuff.writeBytes(MqttUtils.encodeString(message.getClientId()));
                if (message.isWillFlag()) {
                    variableHeaderBuff.writeBytes(MqttUtils.encodeString(message.getWillTopic()));
                    variableHeaderBuff.writeBytes(MqttUtils.encodeFixedLengthContent(message.getWillMessage()));
                }
                if (message.isUserFlag() && message.getUsername() != null) {
                    variableHeaderBuff.writeBytes(MqttUtils.encodeString(message.getUsername()));
                    if (message.isPasswordFlag() && message.getPassword() != null) {
                        variableHeaderBuff.writeBytes(MqttUtils.encodeFixedLengthContent(message.getPassword()));
                    }
                }
            }

            int variableHeaderSize = variableHeaderBuff.readableBytes();
            buff.writeByte(AbstractPacket.CONNECT << 4);
            buff.writeBytes(MqttUtils.encodeRemainingLength(12 + variableHeaderSize));
            buff.writeBytes(staticHeaderBuff).writeBytes(variableHeaderBuff);

            out.writeBytes(buff);
        } finally {
            staticHeaderBuff.release();
            buff.release();
            variableHeaderBuff.release();
        }
    }

}
