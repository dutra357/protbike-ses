package br.com.protbike.records.enuns;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CanalEntrega {
    WHATSAPP("whatsapp"),
    EMAIL("email"),
    SMS("sms"),
    SEM_CANAL("sem-canal");

    private final String codigo;

    CanalEntrega(String codigo) {
        this.codigo = codigo;
    }

    @JsonValue
    public String getCodigo() {
        return codigo;
    }

    @JsonCreator
    public static CanalEntrega fromJson(String value) {
        return CanalEntrega.fromString(value);
    }

    public static CanalEntrega fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Canal de entrega vazio");
        }
        return switch (value.trim().toLowerCase()) {
            case "whatsapp", "wa", "zap" -> WHATSAPP;
            case "email", "e-mail", "mail" -> EMAIL;
            case "sms" -> SMS;
            default -> throw new IllegalArgumentException("Canal de entrega inválido: " + value);
        };
    }
}
