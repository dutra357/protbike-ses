package br.com.protbike.strategy.contract;

import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;

import java.util.concurrent.CompletableFuture;

public interface NotificacaoStrategy {

    CanalEntrega pegarCanal();

    CompletableFuture<ResultadoEnvio> enviarMensagem(BoletoNotificacaoMessage message);

}