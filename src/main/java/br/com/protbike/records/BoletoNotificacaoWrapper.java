package br.com.protbike.records;

import io.quarkus.runtime.annotations.RegisterForReflection;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@RegisterForReflection
public record BoletoNotificacaoWrapper(

        @JsonProperty("lote_id")
        String loteProcessamentoId,
        @JsonProperty("boletos")
        List<BoletoNotificacaoMessage> boletos
) {}