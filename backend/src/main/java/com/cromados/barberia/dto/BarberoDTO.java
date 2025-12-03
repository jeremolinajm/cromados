package com.cromados.barberia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BarberoDTO {
    private Long id;
    @NotBlank private String nombre;
    @NotNull private Long sucursalId;
    private String fotoUrl;
    private String instagram;
    private String facebook;
    private String telefono; // E.164 o local
    private Long telegramChatId;

}
