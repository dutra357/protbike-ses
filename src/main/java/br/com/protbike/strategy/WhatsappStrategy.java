package br.com.protbike.strategy;

import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WhatsappStrategy implements NotificacaoStrategy {


    private static final Logger LOG = Logger.getLogger(WhatsappStrategy.class);

    // Aqui você injetaria seu RestClient da Meta
    // @RestClient
    // MetaClient metaClient;

    @Override
    public CanalEntrega pegarCanal() {
        return CanalEntrega.WHATSAPP;
    }

    @Override
    public ResultadoEnvio enviarMensagem(BoletoNotificacaoMessage message) {
        LOG.infof("Enviando WhatsApp para %s (Protocolo: %s)",
                message.destinatario().telefoneWhatsapp(), message.numeroProtocolo());

        // Lógica de montar o payload da Meta e chamar a API

        return new EnvioSucesso("Enviado com sucesso.");
    }
}
