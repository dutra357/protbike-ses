package br.com.protbike.strategy;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;

public interface NotificacaoStrategy {

    CanalEntrega pegarCanal();

    void enviarMensagem(BoletoNotificacaoMessage message);

}