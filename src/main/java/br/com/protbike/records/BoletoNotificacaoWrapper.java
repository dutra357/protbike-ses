package br.com.protbike.records;

import io.quarkus.runtime.annotations.RegisterForReflection;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@RegisterForReflection
public record BoletoNotificacaoWrapper(

        @JsonProperty("boletos")
        List<BoletoNotificacaoMessage> boletos

) {}