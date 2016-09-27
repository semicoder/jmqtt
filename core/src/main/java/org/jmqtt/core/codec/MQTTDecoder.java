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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.AttributeKey;
import org.jmqtt.core.packet.AbstractPacket;
import org.jmqtt.core.util.MqttUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author andrea
 */
public class MQTTDecoder extends ByteToMessageDecoder {

    //3 = 3.1, 4 = 3.1.1
    public static final AttributeKey<Integer> PROTOCOL_VERSION = AttributeKey.valueOf("version");

    private final Map<Byte, DemuxDecoder> decoderMap = new HashMap<>();

    public MQTTDecoder() {
        decoderMap.put(AbstractPacket.CONNECT, new ConnectDecoder());
        decoderMap.put(AbstractPacket.CONNACK, new ConnAckDecoder());
        decoderMap.put(AbstractPacket.PUBLISH, new PublishDecoder());
        decoderMap.put(AbstractPacket.PUBACK, new PubAckDecoder());
        decoderMap.put(AbstractPacket.SUBSCRIBE, new SubscribeDecoder());
        decoderMap.put(AbstractPacket.SUBACK, new SubAckDecoder());
        decoderMap.put(AbstractPacket.UNSUBSCRIBE, new UnsubscribeDecoder());
        decoderMap.put(AbstractPacket.DISCONNECT, new DisconnectDecoder());
        decoderMap.put(AbstractPacket.PINGREQ, new PingReqDecoder());
        decoderMap.put(AbstractPacket.PINGRESP, new PingRespDecoder());
        decoderMap.put(AbstractPacket.UNSUBACK, new UnsubAckDecoder());
        decoderMap.put(AbstractPacket.PUBCOMP, new PubCompDecoder());
        decoderMap.put(AbstractPacket.PUBREC, new PubRecDecoder());
        decoderMap.put(AbstractPacket.PUBREL, new PubRelDecoder());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();
        if (!MqttUtils.checkHeaderAvailability(in)) {
            in.resetReaderIndex();
            return;
        }
        in.resetReaderIndex();

        byte messageType = MqttUtils.readMessageType(in);

        DemuxDecoder decoder = decoderMap.get(messageType);
        if (decoder == null) {
            throw new CorruptedFrameException("Can't find any suitable decoder for message type: " + messageType);
        }
        decoder.decode(ctx, in, out);
    }
}
