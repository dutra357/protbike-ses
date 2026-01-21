package br.com.protbike.service;

import br.com.protbike.utils.BoletoNotificacaoMessageBuilder;
import br.com.protbike.exceptions.StrategyInvalidaException;
import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.contract.NotificacaoStrategy;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessadorNotificacaoTest {

    @Mock
    private Instance<NotificacaoStrategy> strategyInstances;

    @Mock
    private NotificacaoStrategy emailStrategy;

    @Mock
    private NotificacaoStrategy smsStrategy;

    private ProcessadorNotificacao processador;

    @BeforeEach
    void setup() {
        // Configuração do Mock do CDI Instance
        when(emailStrategy.pegarCanal()).thenReturn(CanalEntrega.EMAIL);
        when(smsStrategy.pegarCanal()).thenReturn(CanalEntrega.SMS);

        List<NotificacaoStrategy> list = Arrays.asList(emailStrategy, smsStrategy);
        when(strategyInstances.iterator()).thenReturn(list.iterator());

        processador = new ProcessadorNotificacao(strategyInstances);
    }

    @Test
    @DisplayName("Sucesso: Deve processar múltiplos canais em paralelo e retornar lista de resultados")
    void deveProcessarMultiplosCanais() {
        // GIVEN
        BoletoNotificacaoMessage msg = BoletoNotificacaoMessageBuilder.novo()
                .comCanais(CanalEntrega.EMAIL, CanalEntrega.SMS)
                .build();

        when(emailStrategy.enviarMensagem(msg))
                .thenReturn(CompletableFuture.completedFuture(new EnvioSucesso(msg.numeroProtocolo())));
        when(smsStrategy.enviarMensagem(msg))
                .thenReturn(CompletableFuture.completedFuture(new EnvioSucesso(msg.numeroProtocolo())));

        // WHEN
        List<ResultadoEnvio> resultados = processador.processarEntrega(msg);

        // THEN
        assertEquals(2, resultados.size());
        assertTrue(resultados.stream().allMatch(r -> r instanceof EnvioSucesso));
        verify(emailStrategy, times(1)).enviarMensagem(msg);
        verify(smsStrategy, times(1)).enviarMensagem(msg);
    }

    @Test
    @DisplayName("Erro: Deve tratar canal solicitado que não possui estratégia mapeada")
    void deveTratarCanalNaoImplementado() {
        // GIVEN: Mensagem pede WHATSAPP, mas só temos EMAIL e SMS no setup
        BoletoNotificacaoMessage msg = BoletoNotificacaoMessageBuilder.novo()
                .comCanais(CanalEntrega.WHATSAPP)
                .build();

        // WHEN
        List<ResultadoEnvio> resultados = processador.processarEntrega(msg);

        // THEN
        assertEquals(1, resultados.size());
        assertTrue(resultados.get(0) instanceof EnvioFalhaNaoRetryavel);
        assertTrue(resultados.get(0).motivo().contains("não implementado"));
    }

    @Test
    @DisplayName("Resiliência: Deve capturar exceção inesperada (Runtime) em uma strategy e não derrubar as outras")
    void deveTratarExceptionNaStrategy() {
        // GIVEN
        BoletoNotificacaoMessage msg = BoletoNotificacaoMessageBuilder.novo()
                .comCanais(CanalEntrega.EMAIL, CanalEntrega.SMS)
                .build();

        // EMAIL completa com erro bizarro (não tratado pela taxonomia interna)
        CompletableFuture<ResultadoEnvio> futureComErro = new CompletableFuture<>();
        futureComErro.completeExceptionally(new RuntimeException("Crash total na rede"));

        when(emailStrategy.enviarMensagem(msg)).thenReturn(futureComErro);
        when(smsStrategy.enviarMensagem(msg))
                .thenReturn(CompletableFuture.completedFuture(new EnvioSucesso(msg.numeroProtocolo())));

        // WHEN
        List<ResultadoEnvio> resultados = processador.processarEntrega(msg);

        // THEN
        assertEquals(2, resultados.size());

        // Um deve ser falha não retryável (o que deu crash)
        assertTrue(resultados.stream().anyMatch(r -> r instanceof EnvioFalhaNaoRetryavel));
        // O outro deve ser sucesso
        assertTrue(resultados.stream().anyMatch(r -> r instanceof EnvioSucesso));
    }

    @Test
    @DisplayName("Configuração: Deve lançar exception se houver duas estratégias para o mesmo canal")
    void deveLancarErroParaEstrategiaDuplicada() {
        // GIVEN: Duas instâncias respondendo pelo mesmo canal
        NotificacaoStrategy s1 = mock(NotificacaoStrategy.class);
        NotificacaoStrategy s2 = mock(NotificacaoStrategy.class);
        when(s1.pegarCanal()).thenReturn(CanalEntrega.EMAIL);
        when(s2.pegarCanal()).thenReturn(CanalEntrega.EMAIL);

        List<NotificacaoStrategy> duplicados = Arrays.asList(s1, s2);
        when(strategyInstances.iterator()).thenReturn(duplicados.iterator());

        // WHEN & THEN
        assertThrows(StrategyInvalidaException.class, () -> new ProcessadorNotificacao(strategyInstances));
    }
}