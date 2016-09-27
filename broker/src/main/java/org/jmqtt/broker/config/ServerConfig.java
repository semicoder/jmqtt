package org.jmqtt.broker.config;

import org.jmqtt.auth.IAuthenticator;
import org.jmqtt.auth.IAuthorizator;
import org.jmqtt.auth.impl.MongoAuthenticator;
import org.jmqtt.auth.impl.PermitAllAuthorizator;
import org.jmqtt.broker.acceptor.NettyAcceptor;
import org.jmqtt.interception.BrokerInterceptor;
import org.jmqtt.persistence.RedissonPersistentStore;
import org.jmqtt.session.IMessagesStore;
import org.jmqtt.session.ISessionsStore;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

/**
 * Created by gavin on 16/9/14.
 */
@Configuration
@EnableConfigurationProperties({AcceptorProperties.class, RedissonProperties.class})
@EnableMongoRepositories("org.jmqtt.**.repository")
public class ServerConfig {

    @Bean
    NettyAcceptor acceptor() {
        return new NettyAcceptor();
    }

    @Bean
    IAuthenticator authenticator() {
        return new MongoAuthenticator();
    }

    @Bean
    IAuthorizator authorizator() {
        return new PermitAllAuthorizator();
    }

    @Bean(destroyMethod = "close")
    RedissonPersistentStore persistenceStore(RedissonProperties properties) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();

        singleServerConfig.setAddress(properties.getAddress());

        singleServerConfig.setDatabase(properties.getDatabase());

        if (!StringUtils.isEmpty(properties.getPassword())) {
            singleServerConfig.setPassword(properties.getPassword());
        }
        return new RedissonPersistentStore(config);
    }

    @Bean
    IMessagesStore messagesStore(RedissonPersistentStore persistenceStore) {
        return persistenceStore.messagesStore();
    }

    @Bean
    ISessionsStore sessionsStore(RedissonPersistentStore persistenceStore, IMessagesStore messagesStore) {
        return persistenceStore.sessionsStore(messagesStore);
    }

    @Bean(destroyMethod = "stop")
    BrokerInterceptor brokerInterceptor() {
        return new BrokerInterceptor(new ArrayList<>());
    }


}
