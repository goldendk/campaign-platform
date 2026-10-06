package dev.campaign.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The only module that knows Spring. Everything here is an adapter: HTTP in, JDBC out, a timer for deadlines,
 * wiring of the framework-free modules. No game rule may live in this module.
 */
@SpringBootApplication
@EnableScheduling
public class GameApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameApplication.class, args);
    }
}
