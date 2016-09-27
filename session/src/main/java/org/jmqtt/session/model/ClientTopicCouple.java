package org.jmqtt.session.model;

public class ClientTopicCouple {
        public final String topicFilter;
        public final String clientId;

        public ClientTopicCouple(String clientId, String topicFilter) {
            this.clientId = clientId;
            this.topicFilter = topicFilter;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClientTopicCouple that = (ClientTopicCouple) o;

            if (topicFilter != null ? !topicFilter.equals(that.topicFilter) : that.topicFilter != null) return false;
            return !(clientId != null ? !clientId.equals(that.clientId) : that.clientId != null);
        }

        @Override
        public int hashCode() {
            int result = topicFilter != null ? topicFilter.hashCode() : 0;
            result = 31 * result + (clientId != null ? clientId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ClientTopicCouple{" +
                    "topicFilter='" + topicFilter + '\'' +
                    ", clientId='" + clientId + '\'' +
                    '}';
        }

    }