package org.jmqtt.auth.impl;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created by gavin on 16/9/21.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    Product findByUsername(String username);

}
