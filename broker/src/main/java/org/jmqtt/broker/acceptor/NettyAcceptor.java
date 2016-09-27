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
package org.jmqtt.broker.acceptor;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import org.jmqtt.broker.config.AcceptorProperties;
import org.jmqtt.broker.handler.IdleTimeoutHandler;
import org.jmqtt.broker.handler.NettyMQTTHandler;
import org.jmqtt.broker.metrics.*;
import org.jmqtt.broker.security.ISslContextCreator;
import org.jmqtt.core.codec.MQTTDecoder;
import org.jmqtt.core.codec.MQTTEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.List;

/**
 * @author andrea
 */
public class NettyAcceptor {

    private static final String MQTT_SUBPROTOCOL_CSV_LIST = "mqtt, mqttv3.1, mqttv3.1.1";

    static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {

        @Override
        protected void decode(ChannelHandlerContext chc, BinaryWebSocketFrame frame, List<Object> out) throws Exception {
            //convert the frame to a ByteBuf
            ByteBuf bb = frame.content();
            //System.out.println("WebSocketFrameToByteBufDecoder decode - " + ByteBufUtil.hexDump(bb));
            bb.retain();
            out.add(bb);
        }
    }

    static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
            //convert the ByteBuf to a WebSocketFrame
            BinaryWebSocketFrame result = new BinaryWebSocketFrame();
            //System.out.println("ByteBufToWebSocketFrameEncoder encode - " + ByteBufUtil.hexDump(bb));
            result.content().writeBytes(bb);
            out.add(result);
        }
    }

    abstract class PipelineInitializer {

        abstract void init(ChannelPipeline pipeline) throws Exception;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NettyAcceptor.class);

    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;

    @Autowired(required = false)
    ISslContextCreator sslContextCreator;

    @Autowired
    NettyMQTTHandler handler;

    @Autowired
    AcceptorProperties props;

    BytesMetricsCollector bytesMetricsCollector = new BytesMetricsCollector();
    MessageMetricsCollector metricsCollector = new MessageMetricsCollector();

    @PostConstruct
    public void initialize() throws IOException {
        bossGroup = new NioEventLoopGroup(props.getBossCount());
        workerGroup = new NioEventLoopGroup(props.getWorkCount());

        initializePlainTCPTransport(handler, props);
        initializeWebSocketTransport(handler, props);

        if (props.getMqttsEnable() || props.getWssEnable()) {
            SSLContext sslContext = sslContextCreator.initSSLContext();
            if (sslContext == null) {
                LOG.error("Can't initialize SSLHandler layer! Exiting, check your configuration of jks");
                return;
            }
            initializeSSLTCPTransport(handler, props, sslContext);
            initializeWSSTransport(handler, props, sslContext);
        }
    }

    private void initFactory(String host, int port, final PipelineInitializer pipeliner) {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        try {
                            pipeliner.init(pipeline);
                        } catch (Throwable th) {
                            LOG.error("Severe error during pipeline creation", th);
                            throw th;
                        }
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        try {
            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(host, port);
            LOG.info("Server binded host: {}, port: {}", host, port);
            f.sync();
        } catch (InterruptedException ex) {
            LOG.error(null, ex);
        }
    }

    private void initializePlainTCPTransport(final NettyMQTTHandler handler, AcceptorProperties props) throws IOException {
        final IdleTimeoutHandler timeoutHandler = new IdleTimeoutHandler();
        String host = props.getHost();
        boolean mqttEnable = props.getMqttEnable();
        if (!mqttEnable) {
            LOG.info("tcp MQTT is disabled");
            return;
        }
        int port = props.getMqttPort();
        initFactory(host, port, new PipelineInitializer() {
            @Override
            void init(ChannelPipeline pipeline) {
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(0, 0, props.getConnectTimeout()));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                //pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.ERROR));
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MQTTDecoder());
                pipeline.addLast("encoder", new MQTTEncoder());
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("handler", handler);
            }
        });
    }

    private void initializeWebSocketTransport(final NettyMQTTHandler handler, AcceptorProperties props) throws IOException {
        if (!props.getWsEnable()) {
            //Do nothing no WebSocket configured
            LOG.info("WebSocket is disabled");
            return;
        }
        int port = props.getWsPort();

        final IdleTimeoutHandler timeoutHandler = new IdleTimeoutHandler();

        String host = props.getHost();
        initFactory(host, port, new PipelineInitializer() {
            @Override
            void init(ChannelPipeline pipeline) {
                pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                pipeline.addLast("webSocketHandler", new WebSocketServerProtocolHandler("/mqtt", MQTT_SUBPROTOCOL_CSV_LIST));
                pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(0, 0, props.getConnectTimeout()));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MQTTDecoder());
                pipeline.addLast("encoder", new MQTTEncoder());
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("handler", handler);
            }
        });
    }

    private void initializeSSLTCPTransport(final NettyMQTTHandler handler, AcceptorProperties props, final SSLContext sslContext) throws IOException {
        if (props.getMqttsEnable()) {
            //Do nothing no SSL configured
            LOG.info("SSL MQTT is disabled");
            return;
        }

        int sslPort = props.getMqttsPort();
        LOG.info("Starting SSL on port {}", sslPort);

        final IdleTimeoutHandler timeoutHandler = new IdleTimeoutHandler();
        String host = props.getHost();
        final boolean needsClientAuth = props.getNeedsClientAuth();
        initFactory(host, sslPort, new PipelineInitializer() {
            @Override
            void init(ChannelPipeline pipeline) throws Exception {
                pipeline.addLast("ssl", createSslHandler(sslContext, needsClientAuth));
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(0, 0, props.getConnectTimeout()));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                //pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.ERROR));
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MQTTDecoder());
                pipeline.addLast("encoder", new MQTTEncoder());
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("handler", handler);
            }
        });
    }

    private void initializeWSSTransport(final NettyMQTTHandler handler, AcceptorProperties props, final SSLContext sslContext) throws IOException {
        if (!props.getWssEnable()) {
            //Do nothing no SSL configured
            LOG.info("SSL websocket is disabled");
            return;
        }
        int sslPort = props.getWssPort();
        final IdleTimeoutHandler timeoutHandler = new IdleTimeoutHandler();
        String host = props.getHost();
        final boolean needsClientAuth = props.getNeedsClientAuth();
        initFactory(host, sslPort, new PipelineInitializer() {
            @Override
            void init(ChannelPipeline pipeline) throws Exception {
                pipeline.addLast("ssl", createSslHandler(sslContext, needsClientAuth));
                pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                pipeline.addLast("webSocketHandler", new WebSocketServerProtocolHandler("/mqtt", MQTT_SUBPROTOCOL_CSV_LIST));
                pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(0, 0, props.getConnectTimeout()));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MQTTDecoder());
                pipeline.addLast("encoder", new MQTTEncoder());
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("handler", handler);
            }
        });
    }

    @PreDestroy
    public void close() {
        if (workerGroup == null) {
            throw new IllegalStateException("Invoked close on an Acceptor that wasn't initialized");
        }
        if (bossGroup == null) {
            throw new IllegalStateException("Invoked close on an Acceptor that wasn't initialized");
        }
        Future workerWaiter = workerGroup.shutdownGracefully();
        Future bossWaiter = bossGroup.shutdownGracefully();

        try {
            workerWaiter.await(100);
        } catch (InterruptedException iex) {
            throw new IllegalStateException(iex);
        }

        try {
            bossWaiter.await(100);
        } catch (InterruptedException iex) {
            throw new IllegalStateException(iex);
        }

        MessageMetrics metrics = metricsCollector.computeMetrics();
        LOG.info("Msg read: {}, msg wrote: {}", metrics.messagesRead(), metrics.messagesWrote());

        BytesMetrics bytesMetrics = bytesMetricsCollector.computeMetrics();
        LOG.info(String.format("Bytes read: %d, bytes wrote: %d", bytesMetrics.readBytes(), bytesMetrics.wroteBytes()));

        LOG.info("jmqtt server closed");
    }

    private ChannelHandler createSslHandler(SSLContext sslContext, boolean needsClientAuth) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        if (needsClientAuth) {
            sslEngine.setNeedClientAuth(true);
        }
        return new SslHandler(sslEngine);
    }
}
