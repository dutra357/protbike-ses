package br.com.protbike.config;

import br.com.protbike.records.BoletoNotificacaoMessage;

import java.util.List;

public class DlqPublisher {

    private String connection;

    public DlqPublisher() {}

    public void enviar(List<BoletoNotificacaoMessage> boleto) {
        // TODO
    }
}
