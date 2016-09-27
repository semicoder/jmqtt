package org.jmqtt.session.model;

import org.jmqtt.core.constant.QosType;

import java.nio.ByteBuffer;

public final class WillMessage {
        private final String topic;
        private final ByteBuffer payload;
        private final boolean retained;
        private final QosType qos;

        public WillMessage(String topic, ByteBuffer payload, boolean retained, QosType qos) {
            this.topic = topic;
            this.payload = payload;
            this.retained = retained;
            this.qos = qos;
        }

        public String getTopic() {
            return topic;
        }

        public ByteBuffer getPayload() {
            return payload;
        }

        public boolean isRetained() {
            return retained;
        }

        public QosType getQos() {
            return qos;
        }

    }