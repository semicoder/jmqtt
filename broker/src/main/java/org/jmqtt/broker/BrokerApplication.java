package org.jmqtt.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;

/**
 * Created by gavin on 16/9/20.
 */
@ComponentScan("org.jmqtt")
@SpringBootApplication
public class BrokerApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
        SpringApplication.run(BrokerApplication.class, args);
    }

}
