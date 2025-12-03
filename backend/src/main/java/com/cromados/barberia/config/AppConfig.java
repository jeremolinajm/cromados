package com.cromados.barberia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuración general de la aplicación.
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate para realizar llamadas HTTP.
     * Usado por WhatsAppMetaService para comunicarse con Meta Cloud API.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
