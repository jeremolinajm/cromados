package com.cromados.barberia.dto;

public class HorarioSemanaDTO {
    private int diaSemana;        // 1=Lun .. 7=Dom
    private String inicio;        // "HH:mm"
    private String fin;           // "HH:mm"

    public HorarioSemanaDTO() {}

    public HorarioSemanaDTO(int diaSemana, String inicio, String fin) {
        this.diaSemana = diaSemana;
        this.inicio = inicio;
        this.fin = fin;
    }

    public int getDiaSemana() { return diaSemana; }
    public void setDiaSemana(int diaSemana) { this.diaSemana = diaSemana; }

    public String getInicio() { return inicio; }
    public void setInicio(String inicio) { this.inicio = inicio; }

    public String getFin() { return fin; }
    public void setFin(String fin) { this.fin = fin; }
}
