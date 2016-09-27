package org.jmqtt.session.model;

import java.io.Serializable;

public class SessionStatus implements Serializable {
    public boolean cleanSession;

    public boolean active;

    public SessionStatus() {
    }

    public SessionStatus(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}