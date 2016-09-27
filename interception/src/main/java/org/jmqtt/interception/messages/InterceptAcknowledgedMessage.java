package org.jmqtt.interception.messages;


import org.jmqtt.session.model.Message;

public class InterceptAcknowledgedMessage {
    final private Message msg;
    private final String username;
    private final String topic;

    public InterceptAcknowledgedMessage(final Message msg, final String topic, final String username) {
        this.msg = msg;
        this.username = username;
        this.topic = topic;
    }

    public Message getMsg() {
        return msg;
    }

    public String getUsername() {
        return username;
    }

    public String getTopic() {
        return topic;
    }
}
