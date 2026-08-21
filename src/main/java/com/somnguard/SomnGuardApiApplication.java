package com.somnguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.somnguard")
@EntityScan(basePackages = "com.somnguard.*.domain.model")
@EnableJpaRepositories(basePackages = "com.somnguard.*.adapter.out.persistence")
public class SomnGuardApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SomnGuardApiApplication.class, args);
    }
}