package br.com.protbike.service;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.strategy.NotificationStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class NotificationProcessor {

    private static final Logger LOG = Logger.getLogger(NotificationProcessor.class);


    private final Map<String, NotificationStrategy> strategies;

    public NotificationProcessor(Instance<NotificationStrategy> strategyInstances) {
        this.strategies = strategyInstances.stream()
                .collect(Collectors.toMap(NotificationStrategy::getChannelName, Function.identity()));
    }

    public void processMessage(BoletoNotificacaoMessage boletoMessage) {

        if (boletoMessage.canais() == null || boletoMessage.canais().isEmpty()) {
            LOG.warn("Mensagem sem canais definidos: " + boletoMessage.processamentoId());
            return;
        }

        for (String canal : boletoMessage.canais()) {
            // Normaliza a string de canais
            String key = canal.toLowerCase().trim();

            if (strategies.containsKey(key)) {
                try {
                    strategies.get(key).send(boletoMessage);
                } catch (Exception e) {
                    LOG.errorf(e, "Erro ao enviar %s para protocolo %s", key, boletoMessage.numeroProtocolo());
                    // Aqui você pode decidir se lança erro para reprocessar a fila ou apenas loga
                }
            } else {
                LOG.warnf("Estratégia não encontrada para o canal: %s", key);
            }
        }
    }
}
