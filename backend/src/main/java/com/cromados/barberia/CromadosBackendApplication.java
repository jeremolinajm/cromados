package com.cromados.barberia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CromadosBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CromadosBackendApplication.class, args);
    }
}
