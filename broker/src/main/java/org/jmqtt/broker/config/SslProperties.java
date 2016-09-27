package org.jmqtt.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created by gavin on 16/9/14.
 */
@ConfigurationProperties("jmqtt.ssl")
public class SslProperties {

    private String keyStorePassword;

    private String keyManagerPassword;

    private String jksPath;

    private Boolean needsClientAuth = false;

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyManagerPassword() {
        return keyManagerPassword;
    }

    public void setKeyManagerPassword(String keyManagerPassword) {
        this.keyManagerPassword = keyManagerPassword;
    }

    public String getJksPath() {
        return jksPath;
    }

    public void setJksPath(String jksPath) {
        this.jksPath = jksPath;
    }

    public Boolean getNeedsClientAuth() {
        return needsClientAuth;
    }

    public void setNeedsClientAuth(Boolean needsClientAuth) {
        this.needsClientAuth = needsClientAuth;
    }
}
