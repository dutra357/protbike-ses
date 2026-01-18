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

    public void processMessage(BoletoNotificacaoMessage msg) {
        if (msg.canais() == null || msg.canais().isEmpty()) {
            LOG.warn("Mensagem sem canais definidos: " + msg.processamentoId());
            return;
        }

        for (String canal : msg.canais()) {
            // Normaliza a string (lowercase, trim) para evitar erros
            String key = canal.toLowerCase().trim();

            if (strategies.containsKey(key)) {
                try {
                    strategies.get(key).send(msg);
                } catch (Exception e) {
                    LOG.errorf(e, "Erro ao enviar %s para protocolo %s", key, msg.numeroProtocolo());
                    // Aqui você pode decidir se lança erro para reprocessar a fila ou apenas loga
                }
            } else {
                LOG.warnf("Estratégia não encontrada para o canal: %s", key);
            }
        }
    }
}
