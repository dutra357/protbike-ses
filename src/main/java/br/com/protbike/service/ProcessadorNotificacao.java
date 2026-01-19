package br.com.protbike.service;

import br.com.protbike.exceptions.StrategyInvalidaException;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.NotificacaoStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import java.util.EnumMap;
import java.util.Map;

import static io.quarkus.arc.ComponentsProvider.LOG;

@ApplicationScoped
public class ProcessadorNotificacao {

    private final Map<CanalEntrega, NotificacaoStrategy> strategies;

    public ProcessadorNotificacao(Instance<NotificacaoStrategy> strategyInstances) {
        EnumMap<CanalEntrega, NotificacaoStrategy> map = new EnumMap<>(CanalEntrega.class);

        for (NotificacaoStrategy strategy : strategyInstances) {

            CanalEntrega key = strategy.pegarCanal();
            NotificacaoStrategy previous = map.putIfAbsent(key, strategy);

            if (previous != null) {
                throw new StrategyInvalidaException(
                        "Estratégia duplicada para canal " + key + ": " +
                                previous.getClass().getName() + " e " + strategy.getClass().getName()
                );
            }
        }

        this.strategies = Map.copyOf(map);
    }

    public void processarEntrega(BoletoNotificacaoMessage boletoMessage) {

        for (CanalEntrega canal : boletoMessage.canais()) {
            NotificacaoStrategy strategy = strategies.get(canal);

            if (strategy == null) {
                LOG.warnf("Estratégia não encontrada para o canal: %s, Protocolo: %s : ",
                        canal, boletoMessage.numeroProtocolo());
                continue;
            }

            strategy.enviarMensagem(boletoMessage);
        }
    }
}