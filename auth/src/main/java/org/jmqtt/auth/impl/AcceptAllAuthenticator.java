package org.jmqtt.auth.impl;


import org.jmqtt.auth.IAuthenticator;

/**
 * Created by andrea on 8/23/14.
 */
public class AcceptAllAuthenticator implements IAuthenticator {
    public boolean checkValid(String clientId, String username, byte[] password, boolean allowZeroByteClientId) {
        return true;
    }
}
