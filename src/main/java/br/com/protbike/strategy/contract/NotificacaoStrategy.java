package br.com.protbike.strategy.contract;

import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;

public interface NotificacaoStrategy {

    CanalEntrega pegarCanal();

    ResultadoEnvio enviarMensagem(BoletoNotificacaoMessage message);

}