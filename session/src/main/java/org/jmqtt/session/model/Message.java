package org.jmqtt.session.model;

import org.jmqtt.core.constant.QosType;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class Message implements Serializable {
        private QosType qos;
        private byte[] payload;
        private String topic;
        private boolean retained;
        private String clientId;
        //Optional attribute, available only fo QoS 1 and 2
        private Integer packetId;
        private String msgId;

        public Message() {
        }

        public Message(byte[] message, QosType qos, String topic) {
            this.qos = qos;
            this.payload = message;
            this.topic = topic;
        }

        public QosType getQos() {
            return qos;
        }

        public ByteBuffer getPayloadBuffer() {
            return (ByteBuffer) ByteBuffer.allocate(payload.length).put(payload).flip();
        }

        public String getTopic() {
            return topic;
        }

        public void setMsgId(String msgId) {
            this.msgId = msgId;
        }

        public String getMsgId() {
            return msgId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String m_clientID) {
            this.clientId = m_clientID;
        }

        public ByteBuffer getMessage() {
            return ByteBuffer.wrap(payload);
        }

        public void setRetained(boolean retained) {
            this.retained = retained;
        }

        public boolean isRetained() {
            return retained;
        }

        public void setQos(QosType qos) {
            this.qos = qos;
        }

        public void setPayload(byte[] payload) {
            this.payload = payload;
        }

        public byte[] getPayload() {
            return payload;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public Integer getPacketId() {
            return packetId;
        }

        public void setPacketId(Integer packetId) {
            this.packetId = packetId;
        }

        @Override
        public String toString() {
            return "PublishEvent{" +
                    "packetId=" + packetId +
                    ", clientId='" + clientId + '\'' +
                    ", m_retain=" + retained +
                    ", qos=" + qos +
                    ", topic='" + topic + '\'' +
                    '}';
        }
    }