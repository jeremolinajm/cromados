// src/main/java/com/cromados/barberia/service/WhatsAppRecordatorioScheduler.java
package com.cromados.barberia.service;

import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduler que envía recordatorios de WhatsApp 6 horas antes de cada turno confirmado.
 * Se ejecuta cada hora para verificar si hay turnos próximos.
 */
@Slf4j
@Service
public class WhatsAppRecordatorioScheduler {

    private final TurnoRepository turnoRepository;
    private final WhatsAppMetaService whatsAppMetaService;
    private final TwilioService twilioService;

    @Value("${whatsapp.meta.sucursal1.phone:}")
    private String sucursal1Phone;

    @Value("${whatsapp.meta.sucursal2.phone:}")
    private String sucursal2Phone;

    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public WhatsAppRecordatorioScheduler(
            TurnoRepository turnoRepository,
            WhatsAppMetaService whatsAppMetaService,
            TwilioService twilioService
    ) {
        this.turnoRepository = turnoRepository;
        this.whatsAppMetaService = whatsAppMetaService;
        this.twilioService = twilioService;
    }

    /**
     * Tarea programada que se ejecuta cada hora (a los 5 minutos de cada hora).
     * Busca turnos confirmados que ocurrirán en aproximadamente 6 horas
     * y les envía un recordatorio por WhatsApp.
     *
     * Cron: "0 5 * * * *" = A los 5 minutos de cada hora
     */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void enviarRecordatorios() {
        log.info("[Scheduler] Iniciando envío de recordatorios...");

        try {
            // Usar la hora de Argentina
            LocalDateTime ahora = ZonedDateTime.now(ZONA_ARGENTINA).toLocalDateTime();
            LocalDateTime en6Horas = ahora.plusHours(6);

            // Buscar turnos en ventana de 6-7 horas (para cubrir la hora completa)
            LocalDateTime en7Horas = ahora.plusHours(7);

            log.info("[Scheduler] Buscando turnos entre {} y {} (hora Argentina)", en6Horas, en7Horas);

            // Buscar turnos confirmados en el rango de fechas
            List<Turno> turnosCandidatos = turnoRepository.findByFechaBetween(
                    en6Horas.toLocalDate(),
                    en7Horas.toLocalDate()
            );

            log.info("[Scheduler] Encontrados {} turnos en el rango de fechas", turnosCandidatos.size());

            int enviados = 0;
            int saltados = 0;
            int errores = 0;

            for (Turno turno : turnosCandidatos) {
                // Verificar si el turno está confirmado
                if (!esturnoConfirmado(turno)) {
                    continue;
                }

                // Verificar si ya se envió el recordatorio
                if (Boolean.TRUE.equals(turno.getRecordatorioEnviado())) {
                    saltados++;
                    continue;
                }

                // Calcular fecha/hora exacta del turno
                LocalDateTime fechaHoraTurno = LocalDateTime.of(turno.getFecha(), turno.getHora());

                // Verificar si el turno está en la ventana de 6-7 horas
                if (fechaHoraTurno.isAfter(en6Horas) && fechaHoraTurno.isBefore(en7Horas)) {
                    try {
                        enviarRecordatorio(turno);

                        // Marcar como enviado
                        turno.setRecordatorioEnviado(true);
                        turnoRepository.save(turno);

                        enviados++;
                        log.info("[Scheduler] Recordatorio enviado - Turno #{} - Cliente: {} - Fecha: {} {}",
                                turno.getId(), turno.getClienteNombre(),
                                turno.getFecha(), turno.getHora());
                    } catch (Exception e) {
                        errores++;
                        log.error("[Scheduler] Error enviando recordatorio para turno #{}: {}",
                                turno.getId(), e.getMessage(), e);
                    }
                }
            }

            log.info("[Scheduler] Resumen: {} enviados, {} saltados (ya enviados), {} errores",
                    enviados, saltados, errores);

        } catch (Exception e) {
            log.error("[Scheduler] Error ejecutando scheduler de recordatorios: {}", e.getMessage(), e);
        }
    }

    /**
     * Verifica si un turno está confirmado (pagado, CONFIRMADO, o BLOQUEADO desde Telegram).
     */
    private boolean esturnoConfirmado(Turno turno) {
        return Boolean.TRUE.equals(turno.getPagoConfirmado())
                || "CONFIRMADO".equalsIgnoreCase(turno.getEstado())
                || "BLOQUEADO".equalsIgnoreCase(turno.getEstado());
    }

    /**
     * Envía el recordatorio de WhatsApp para un turno.
     * Usa Twilio para enviar el recordatorio con el teléfono de la sucursal correspondiente.
     */
    private void enviarRecordatorio(Turno turno) {
        String fechaFormateada = turno.getFecha().format(FECHA_FORMATTER);
        String horaFormateada = turno.getHora().format(HORA_FORMATTER);

        // Determinar teléfono de la sucursal (basado en ID: 1 o 2)
        String telefonoSucursal = turno.getSucursal().getId() == 1 ? sucursal1Phone : sucursal2Phone;

        try {
            // Enviar con Twilio
            twilioService.sendRecordatorio(
                    turno.getClienteTelefono(),
                    turno.getClienteNombre(),
                    fechaFormateada,
                    horaFormateada,
                    turno.getBarbero().getNombre(),
                    turno.getSucursal().getNombre(),
                    telefonoSucursal
            );
            log.info("[Recordatorio][Twilio] Enviado a {} - Turno #{}", turno.getClienteNombre(), turno.getId());
        } catch (Exception e) {
            log.error("[Recordatorio][Twilio] Error: {}", e.getMessage(), e);
            // Intentar con Meta como fallback (opcional)
            try {
                whatsAppMetaService.sendRecordatorio(
                        turno.getClienteTelefono(),
                        turno.getClienteNombre(),
                        fechaFormateada,
                        horaFormateada,
                        turno.getBarbero().getNombre(),
                        turno.getSucursal().getNombre()
                );
                log.info("[Recordatorio][Meta] Enviado como fallback - Turno #{}", turno.getId());
            } catch (Exception e2) {
                log.error("[Recordatorio][Meta] Error en fallback: {}", e2.getMessage());
                throw e; // Re-lanzar el error original
            }
        }
    }

    /**
     * Método manual para testear el envío de recordatorios.
     * Puede ser llamado desde un endpoint de testing.
     */
    public void enviarRecordatorioManual(Long turnoId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));

        if (!esturnoConfirmado(turno)) {
            throw new IllegalStateException("El turno no está confirmado");
        }

        log.info("[Scheduler Manual] Enviando recordatorio para turno #{}", turnoId);
        enviarRecordatorio(turno);

        turno.setRecordatorioEnviado(true);
        turnoRepository.save(turno);
    }
}
