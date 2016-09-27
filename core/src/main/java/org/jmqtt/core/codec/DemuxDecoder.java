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
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.AttributeMap;
import org.jmqtt.core.constant.QosType;
import org.jmqtt.core.packet.AbstractPacket;
import org.jmqtt.core.util.MqttUtils;

import java.util.List;

/**
 * @author andrea
 */
abstract class DemuxDecoder {
    abstract void decode(AttributeMap ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * Decodes the first 2 bytes of the MQTT packet.
     * The first byte contain the packet operation code and the flags,
     * the second byte and more contains the overall packet length.
     */
    protected boolean decodeCommonHeader(AbstractPacket message, ByteBuf in) {
        return genericDecodeCommonHeader(message, null, in);
    }

    /**
     * Do the same as the @see#decodeCommonHeader but having a strong validation on the flags values
     */
    protected boolean decodeCommonHeader(AbstractPacket message, int expectedFlags, ByteBuf in) {
        return genericDecodeCommonHeader(message, expectedFlags, in);
    }


    private boolean genericDecodeCommonHeader(AbstractPacket message, Integer expectedFlagsOpt, ByteBuf in) {
        //Common decoding part
        if (in.readableBytes() < 2) {
            return false;
        }
        byte h1 = in.readByte();
        byte messageType = (byte) ((h1 & 0x00F0) >> 4);

        byte flags = (byte) (h1 & 0x0F);
        if (expectedFlagsOpt != null) {
            int expectedFlags = expectedFlagsOpt;
            if ((byte) expectedFlags != flags) {
                String hexExpected = Integer.toHexString(expectedFlags);
                String hexReceived = Integer.toHexString(flags);
                throw new CorruptedFrameException(String.format("Received a message with fixed header flags (%s) != expected (%s)", hexReceived, hexExpected));
            }
        }

        boolean dupFlag = ((byte) ((h1 & 0x0008) >> 3) == 1);
        byte qosLevel = (byte) ((h1 & 0x0006) >> 1);
        boolean retainFlag = ((byte) (h1 & 0x0001) == 1);
        int remainingLength = MqttUtils.decodeRemainingLenght(in);
        if (remainingLength == -1) {
            return false;
        }

        message.setMessageType(messageType);
        message.setDupFlag(dupFlag);
        try {
            message.setQos(QosType.valueOf(qosLevel));
        } catch (IllegalArgumentException e) {
            throw new CorruptedFrameException(String.format("Received an invalid QOS: %s", e.getMessage()), e);
        }
        message.setRetainFlag(retainFlag);
        message.setRemainingLength(remainingLength);
        return true;
    }
}
