package com.cromados.barberia.service.telegram;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.repository.BarberoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maneja las sesiones de conversación del bot de Telegram.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSessionManager {

    private final BarberoRepository barberoRepo;

    // Estado de conversación por chatId
    private final Map<Long, SessionState> sessions = new ConcurrentHashMap<>();

    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    /**
     * Obtiene o crea una sesión para un chatId.
     */
    public SessionState getOrCreateSession(Long chatId) {
        return sessions.computeIfAbsent(chatId, k -> new SessionState());
    }

    /**
     * Obtiene una sesión existente o null si no existe.
     */
    public SessionState getSession(Long chatId) {
        return sessions.get(chatId);
    }

    /**
     * Elimina una sesión.
     */
    public void removeSession(Long chatId) {
        sessions.remove(chatId);
    }

    /**
     * Valida que el barbero esté vinculado al chatId.
     * Si está vinculado, actualiza la sesión con el barbero.
     *
     * @return Barbero vinculado o null si no está vinculado
     */
    public Barbero validateAndGetBarbero(Long chatId) {
        Barbero barbero = barberoRepo.findByTelegramChatId(chatId).orElse(null);

        if (barbero == null) {
            return null;
        }

        // Actualizar sesión con barbero
        SessionState session = getOrCreateSession(chatId);
        session.setBarbero(barbero);
        session.touch();

        return barbero;
    }

    /**
     * Limpia sesiones expiradas (más de 30 minutos sin actividad).
     * Se ejecuta cada hora.
     */
    @Scheduled(fixedRate = 3600000) // 1 hora
    public void cleanExpiredSessions() {
        Instant now = Instant.now();
        int removed = 0;

        var it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            SessionState state = entry.getValue();

            if (Duration.between(state.getLastActivity(), now).compareTo(SESSION_TIMEOUT) > 0) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.info("[Telegram] Limpiadas {} sesiones expiradas", removed);
        }
    }

    /**
     * Obtiene el número de sesiones activas.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
