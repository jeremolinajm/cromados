package com.cromados.barberia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class StaticResourcesConfig {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // evitar NPE y normalizar path
                String base = (uploadDir == null || uploadDir.isBlank()) ? "./uploads" : uploadDir;
                if (!base.endsWith("/")) base = base + "/";
                String location = "file:" + base;
                registry.addResourceHandler("/uploads/**").addResourceLocations(location);
            }

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173","https://*.trycloudflare.com")
                        .allowedMethods("GET","POST","PUT","DELETE","PATCH","OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
