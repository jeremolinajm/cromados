package com.cromados.barberia.service;


import com.cromados.barberia.model.Turno;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    public void notifyPagoParcial(Turno t) {
        var b = t.getBarbero();
        String msg = "Seña 50% recibida para " + t.getFecha() + " " + t.getHora()
                + ". Cobrar el otro 50% al terminar.";
        System.out.println("[NOTIFY] " + (b!=null? b.getNombre() : "?") + " -> " + msg);
        // TODO: integrar Twilio/WhatsApp usando b.getTelefono()
    }
}
