package org.jmqtt.interception.messages;


import org.jmqtt.core.packet.PublishPacket;

import java.nio.ByteBuffer;

/**
 * @author Wagner Macedo
 */
public class InterceptPublishMessage extends InterceptAbstractMessage {
    private final PublishPacket msg;
    private final String clientID;
    private final String username;

    public InterceptPublishMessage(PublishPacket msg, String clientID, String username) {
        super(msg);
        this.msg = msg;
        this.clientID = clientID;
        this.username = username;
    }

    public String getTopicName() {
        return msg.getTopicName();
    }

    public ByteBuffer getPayload() {
        return msg.getPayload();
    }

    public String getClientID() {
        return clientID;
    }

    public String getUsername() {
        return username;
    }
}
