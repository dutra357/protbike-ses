package br.com.protbike.strategy;

import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;

public interface NotificacaoStrategy {

    CanalEntrega pegarCanal();

    ResultadoEnvio enviarMensagem(BoletoNotificacaoMessage message);

}