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
package org.jmqtt.broker.handler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.CorruptedFrameException;
import org.jmqtt.broker.process.ProtocolProcessor;
import org.jmqtt.broker.util.NettyUtils;
import org.jmqtt.core.packet.*;
import org.jmqtt.core.util.MqttUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.jmqtt.core.packet.AbstractPacket.*;


/**
 * @author andrea
 */
@Sharable
@Component
public class NettyMQTTHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(NettyMQTTHandler.class);

    @Autowired
    ProtocolProcessor processor;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        AbstractPacket msg = (AbstractPacket) message;
        LOG.info("Received a message of type {}", MqttUtils.msgType2String(msg.getMessageType()));
        try {
            switch (msg.getMessageType()) {
                case CONNECT:
                    processor.processConnect(ctx.channel(), (ConnectPacket) msg);
                    break;
                case PUBLISH:
                    processor.processPublish(ctx.channel(), (PublishPacket) msg);
                    break;
                case PUBACK:
                    processor.processPubAck(ctx.channel(), (PubAckPacket) msg);
                    break;
                case PUBREC:
                    processor.processPubRec(ctx.channel(), (PubRecPacket) msg);
                    break;
                case PUBREL:
                    processor.processPubRel(ctx.channel(), (PubRelPacket) msg);
                    break;
                case PUBCOMP:
                    processor.processPubComp(ctx.channel(), (PubCompPacket) msg);
                    break;
                case SUBSCRIBE:
                    processor.processSubscribe(ctx.channel(), (SubscribePacket) msg);
                    break;
                case UNSUBSCRIBE:
                    processor.processUnsubscribe(ctx.channel(), (UnsubscribePacket) msg);
                    break;
                case PINGREQ:
                    PingRespPacket pingResp = new PingRespPacket();
                    ctx.writeAndFlush(pingResp);
                    break;
                case DISCONNECT:
                    processor.processDisconnect(ctx.channel());
                    break;
            }
        } catch (Exception ex) {
            LOG.error("Bad error in processing the message", ex);
            ctx.fireExceptionCaught(ex);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = NettyUtils.clientId(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            //if the channel was of a correctly connected client, inform messaging
            //else it was of a not completed CONNECT message or sessionStolen
            boolean stolen = false;
            Boolean stolenAttr = NettyUtils.sessionStolen(ctx.channel());
            if (stolenAttr != null && stolenAttr == Boolean.TRUE) {
                stolen = true;
            }
            processor.processConnectionLost(clientID, stolen, ctx.channel());
        }
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof CorruptedFrameException) {
            //something goes bad with decoding
            LOG.warn("Error decoding a packet, probably a bad formatted packet, message: " + cause.getMessage());
        } else {
            LOG.error("Ugly error on networking", cause);
        }
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            processor.notifyChannelWritable(ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }
}
