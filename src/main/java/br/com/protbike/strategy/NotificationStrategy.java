package br.com.protbike.strategy;

import br.com.protbike.records.BoletoNotificacaoMessage;

public interface NotificationStrategy {

    String getChannelName();

    void send(BoletoNotificacaoMessage message);

}