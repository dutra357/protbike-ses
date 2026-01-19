package br.com.protbike.service;

import br.com.protbike.metrics.Metricas;
import br.com.protbike.exceptions.StrategyInvalidaException;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.NotificacaoStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.EnumMap;
import java.util.Map;

import static io.quarkus.arc.ComponentsProvider.LOG;

@ApplicationScoped
public class ProcessadorNotificacao {

    private final Map<CanalEntrega, NotificacaoStrategy> strategies;
    private final Metricas metricas;

    @Inject
    public ProcessadorNotificacao(Instance<NotificacaoStrategy> strategyInstances, Metricas metricas) {
        this.metricas = metricas;
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

            if (boletoMessage.canais() == null || boletoMessage.canais().isEmpty()) {
                metricas.boletosFalha++;
                LOG.warnf(
                        "Mensagem/boleto sem canais tenantId=%s protocoloId=%s processamentoId=s%",
                        boletoMessage.tenantId(),
                        boletoMessage.numeroProtocolo(),
                        boletoMessage.processamentoId()
                );
                return;
            }

            if (strategy == null) {
                LOG.warnf(
                        "Canal sem strategy tenantId=%s protocoloId=%s canal=%s processamentoId=s%",
                        boletoMessage.tenantId(),
                        boletoMessage.numeroProtocolo(),
                        canal,
                        boletoMessage.processamentoId()
                );

                continue;
            }

            try {
                strategy.enviarMensagem(boletoMessage);
                metricas.boletosSucesso++;

            } catch (Exception e) {
                metricas.boletosFalha++;
                LOG.errorf(e,
                        "Falha no envio: canal=%s protocoloId=%s tenantId=%s processamentoId=s%",
                        canal,
                        boletoMessage.numeroProtocolo(),
                        boletoMessage.tenantId(),
                        boletoMessage.processamentoId()
                );
            }
        }
    }
}