// src/main/java/com/cromados/barberia/config/TelegramConfig.java
package com.cromados.barberia.config;

import com.cromados.barberia.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class TelegramConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService botService) {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(botService);
            log.info("[Telegram] Bot registrado exitosamente");
            return api;
        } catch (TelegramApiException e) {
            log.error("[Telegram] Error registrando bot: {}", e.getMessage());
            throw new RuntimeException("No se pudo inicializar Telegram Bot", e);
        }
    }
}