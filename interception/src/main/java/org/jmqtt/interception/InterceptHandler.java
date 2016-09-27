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
package org.jmqtt.interception;


import org.jmqtt.core.packet.AbstractPacket;
import org.jmqtt.interception.messages.*;

/**
 * This interface is used to inject code for intercepting broker events.
 * <p>
 * The events can act only as observers.
 * <p>
 * Almost every method receives a subclass of {@link AbstractPacket}, except
 * <code>onDisconnect</code> that receives the client id string and
 * <code>onSubscribe</code> and <code>onUnsubscribe</code>
 *
 * @author Wagner Macedo
 */
public interface InterceptHandler {

    void onConnect(InterceptConnectMessage msg);

    void onDisconnect(InterceptDisconnectMessage msg);

    void onPublish(InterceptPublishMessage msg);

    void onSubscribe(InterceptSubscribeMessage msg);

    void onUnsubscribe(InterceptUnsubscribeMessage msg);

    void onMessageAcknowledged(InterceptAcknowledgedMessage msg);
}
