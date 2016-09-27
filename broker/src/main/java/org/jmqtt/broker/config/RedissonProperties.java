package org.jmqtt.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created by gavin on 16/9/14.
 */
@ConfigurationProperties("jmqtt.redisson")
public class RedissonProperties {

    private String address = "127.0.0.1:6379";

    private Integer database = 0;

    private String password;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getDatabase() {
        return database;
    }

    public void setDatabase(Integer database) {
        this.database = database;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
