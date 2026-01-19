package br.com.protbike.service;

import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
import br.com.protbike.metrics.Metricas;
import br.com.protbike.exceptions.StrategyInvalidaException;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.NotificacaoStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    public List<ResultadoEnvio> processarEntrega(BoletoNotificacaoMessage boletoMessage) {

        List<ResultadoEnvio> resultados = new ArrayList<>();

        for (CanalEntrega canal : boletoMessage.canais()) {
            NotificacaoStrategy strategy = strategies.get(canal);

            if (strategy == null) {
               resultados.add(
                        new EnvioFalhaNaoRetryavel(
                                boletoMessage.numeroProtocolo(),
                                "Canal sem strategy (null): " + canal
                        )
                );

                LOG.warnf(
                        "Mensagem/boleto sem canais (null) tenantId=%s protocoloId=%s processamentoId=s%",
                        boletoMessage.tenantId(),
                        boletoMessage.numeroProtocolo(),
                        boletoMessage.processamentoId()
                );
                continue;
            }

            ResultadoEnvio resultado = strategy.enviarMensagem(boletoMessage);
            resultados.add(resultado);
        }
        return resultados;
    }
}