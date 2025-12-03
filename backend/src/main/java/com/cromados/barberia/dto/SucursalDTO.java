package com.cromados.barberia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SucursalDTO {
    private Long id;
    @NotBlank private String nombre;
    @NotBlank private String direccion;
    private String fotoUrl;

}
