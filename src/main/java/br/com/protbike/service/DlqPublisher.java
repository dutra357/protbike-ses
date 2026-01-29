package br.com.protbike.service;

import br.com.protbike.records.BoletoNotificacaoMessage;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class DlqPublisher {

    private String connection;

    public DlqPublisher() {}

    public void enviar(List<BoletoNotificacaoMessage> boleto) {
        // TODO
    }
}
