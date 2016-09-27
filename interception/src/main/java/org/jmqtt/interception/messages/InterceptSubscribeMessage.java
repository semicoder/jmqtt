package org.jmqtt.interception.messages;


import org.jmqtt.core.constant.QosType;
import org.jmqtt.session.model.Subscription;

/**
 * @author Wagner Macedo
 */
public class InterceptSubscribeMessage {
    private final Subscription subscription;
    private final String username;

    public InterceptSubscribeMessage(Subscription subscription, String username) {
        this.subscription = subscription;
        this.username = username;
    }

    public String getClientID() {
        return subscription.getClientId();
    }

    public QosType getRequestedQos() {
        return subscription.getRequestedQos();
    }

    public String getTopicFilter() {
        return subscription.getTopicFilter();
    }

    public String getUsername() {
        return username;
    }
}
