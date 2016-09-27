package org.jmqtt.auth.impl;

import org.jmqtt.auth.IAuthenticator;
import org.jmqtt.auth.impl.model.Product;
import org.jmqtt.auth.impl.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;

/**
 * Created by gavin on 16/9/21.
 */
public class MongoAuthenticator implements IAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(MongoAuthenticator.class);

    @Autowired
    ProductRepository repository;

    @Override
    public boolean checkValid(String clientId, String username, byte[] password, boolean allowZeroByteClientId) {

        Product product = repository.findByUsername(username);

        if (product == null) {
            return false;
        }

        PasswordEncoder passwordEncoder = new StandardPasswordEncoder(product.getSalt());

        if (!passwordEncoder.matches(new String(password), product.getPassword())) {
            return false;
        }

        if (!allowZeroByteClientId && !product.getClients().contains(clientId)) {
            return false;
        }

        return true;
    }

}
