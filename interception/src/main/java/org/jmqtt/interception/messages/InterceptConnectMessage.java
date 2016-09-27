package org.jmqtt.interception.messages;


import org.jmqtt.core.packet.ConnectPacket;

/**
 * @author Wagner Macedo
 */
public class InterceptConnectMessage extends InterceptAbstractMessage {
    private final ConnectPacket msg;

    public InterceptConnectMessage(ConnectPacket msg) {
        super(msg);
        this.msg = msg;
    }

    public String getClientID() {
        return msg.getClientId();
    }

    public boolean isCleanSession() {
        return msg.isCleanSession();
    }

    public int getKeepAlive() {
        return msg.getKeepAlive();
    }

    public boolean isPasswordFlag() {
        return msg.isPasswordFlag();
    }

    public byte getProtocolVersion() {
        return msg.getProtocolVersion();
    }

    public String getProtocolName() {
        return msg.getProtocolName();
    }

    public boolean isUserFlag() {
        return msg.isUserFlag();
    }

    public boolean isWillFlag() {
        return msg.isWillFlag();
    }

    public byte getWillQos() {
        return msg.getWillQos();
    }

    public boolean isWillRetain() {
        return msg.isWillRetain();
    }

    public String getUsername() {
        return msg.getUsername();
    }

    public byte[] getPassword() {
        return msg.getPassword();
    }

    public String getWillTopic() {
        return msg.getWillTopic();
    }

    public byte[] getWillMessage() {
        return msg.getWillMessage();
    }
}
