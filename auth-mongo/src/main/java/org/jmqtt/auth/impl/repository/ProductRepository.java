package org.jmqtt.auth.impl.repository;

import org.jmqtt.auth.impl.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by gavin on 16/9/21.
 */
public interface ProductRepository extends MongoRepository<Product, String> {
    Product findByUsername(String username);
}
