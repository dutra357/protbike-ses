package br.com.protbike.service;

import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.exceptions.StrategyInvalidaException;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.contract.NotificacaoStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static io.quarkus.arc.ComponentsProvider.LOG;

@ApplicationScoped
public class ProcessadorNotificacao {

    private final Map<CanalEntrega, NotificacaoStrategy> strategies;

    @Inject
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

    public List<ResultadoEnvio> processarEntrega(BoletoNotificacaoMessage boletoMessage) {

        List<CompletableFuture<ResultadoEnvio>> futures = new ArrayList<>();

        for (CanalEntrega canal : boletoMessage.canais()) {
            NotificacaoStrategy strategy = strategies.get(canal);

            if (strategy == null) {
                LOG.errorf("Configuração inconsistente: canal %s solicitado mas não implementado. Protocolo: %s",
                        canal, boletoMessage.numeroProtocolo());

                // Como não há strategy, cria um future já completado com falha
                futures.add(CompletableFuture.completedFuture(
                        new EnvioFalhaNaoRetryavel(boletoMessage.numeroProtocolo(), "Canal não implementado: " + canal)
                ));
                continue;
            }

            // Disparar o envio assíncrono
            CompletableFuture<ResultadoEnvio> future = strategy.enviarMensagem(boletoMessage)
                    .exceptionally(e -> {
                        // Captura erros fora da escada de catch da strategy
                        LOG.errorf(e, "Erro fatal não tratado na strategy %s para o protocolo %s",
                                canal, boletoMessage.numeroProtocolo());

                        return new EnvioFalhaNaoRetryavel(
                                boletoMessage.numeroProtocolo(),
                                "Erro interno inesperado: " + e.getMessage()
                        );
                    });

            futures.add(future);
        }

        // Aguardar todas as estratégias terminarem em paralelo
        // allOf cria um future que completa quando todos os envios terminarem
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList())
                .join();
    }
}