package br.com.protbike.service;

import br.com.protbike.exceptions.StrategyInvalidaException;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.NotificacaoStrategy;
import br.com.protbike.utils.BoletoTestHelper;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessadorNotificacaoTest {

    @Mock
    NotificacaoStrategy emailStrategy;

    @Mock
    NotificacaoStrategy whatsappStrategy;

    @Mock
    Instance<NotificacaoStrategy> instanceMock;

    ProcessadorNotificacao processor;

    private static Instance<NotificacaoStrategy> instanceCom(NotificacaoStrategy... strategies) {
        @SuppressWarnings("unchecked")
        Instance<NotificacaoStrategy> instance = mock(Instance.class);

        List<NotificacaoStrategy> list = Arrays.asList(strategies);
        when(instance.iterator()).thenReturn(list.iterator());

        return instance;
    }

    @BeforeEach
    void setup() {
        when(emailStrategy.pegarCanal()).thenReturn(CanalEntrega.EMAIL);
        when(whatsappStrategy.pegarCanal()).thenReturn(CanalEntrega.WHATSAPP);

        // O construtor usa "for (NotificacaoStrategy s : strategyInstances)"
        // => isso chama strategyInstances.iterator(), não stream().
        List<NotificacaoStrategy> strategies = Arrays.asList(emailStrategy, whatsappStrategy);
        Iterator<NotificacaoStrategy> it = strategies.iterator();
        when(instanceMock.iterator()).thenReturn(it);

        processor = new ProcessadorNotificacao(instanceMock);

        // O construtor já chamou pegarCanal(); limpamos para os testes verificarem só o processarEntrega
        clearInvocations(emailStrategy, whatsappStrategy);
    }

    @Test
    void deveAcionarApenasWhatsapp() {
        var msg = BoletoTestHelper.builder()
                .withCanais(CanalEntrega.WHATSAPP)
                .build();

        processor.processarEntrega(msg);

        verify(whatsappStrategy).enviarMensagem(msg);
        verify(emailStrategy, never()).enviarMensagem(any());
        verifyNoMoreInteractions(emailStrategy, whatsappStrategy);
    }

    @Test
    void deveAcionarAmbosOsCanais() {
        var msg = BoletoTestHelper.builder()
                .withCanais(CanalEntrega.WHATSAPP, CanalEntrega.EMAIL)
                .build();

        processor.processarEntrega(msg);

        verify(whatsappStrategy).enviarMensagem(msg);
        verify(emailStrategy).enviarMensagem(msg);
        verifyNoMoreInteractions(emailStrategy, whatsappStrategy);
    }

    @Test
    void deveIgnorarCanalSemEstrategiaSemQuebrar() {
        var msg = BoletoTestHelper.builder()
                .withCanais(CanalEntrega.SMS, CanalEntrega.EMAIL)
                .build();

        processor.processarEntrega(msg);

        verify(emailStrategy).enviarMensagem(msg);
        verify(whatsappStrategy, never()).enviarMensagem(any());
        verifyNoMoreInteractions(emailStrategy, whatsappStrategy);
    }

    @Test
    void deveConstruirMesmoSemNenhumaStrategy() {
        Instance<NotificacaoStrategy> emptyInstance = instanceCom();

        ProcessadorNotificacao processor = new ProcessadorNotificacao(emptyInstance);

        var msg = BoletoTestHelper.builder()
                .withCanais(CanalEntrega.EMAIL)
                .build();

        // não deve lançar; apenas ignorar por não haver strategy
        assertDoesNotThrow(() -> processor.processarEntrega(msg));
    }

    @Test
    void deveIgnorarTodosOsCanaisQuandoNenhumaStrategyRegistrada() {
        Instance<NotificacaoStrategy> emptyInstance = instanceCom();
        ProcessadorNotificacao processor = new ProcessadorNotificacao(emptyInstance);

        var msg = BoletoTestHelper.builder()
                .withCanais(CanalEntrega.EMAIL, CanalEntrega.WHATSAPP, CanalEntrega.SMS)
                .build();

        assertDoesNotThrow(() -> processor.processarEntrega(msg));
        // sem mocks de strategies aqui, então a verificação é “não explodiu”
    }

    @Test
    void deveFalharSeDuasStrategiesDeclararemOMesmoCanal() {
        NotificacaoStrategy s1 = mock(NotificacaoStrategy.class);
        NotificacaoStrategy s2 = mock(NotificacaoStrategy.class);

        when(s1.pegarCanal()).thenReturn(CanalEntrega.EMAIL);
        when(s2.pegarCanal()).thenReturn(CanalEntrega.EMAIL);

        Instance<NotificacaoStrategy> instance = instanceCom(s1, s2);

        assertThrows(StrategyInvalidaException.class, () -> new ProcessadorNotificacao(instance));
    }

    @Test
    void deveFalharSeUmaStrategyRetornarCanalNuloNoConstrutor() {
        NotificacaoStrategy s1 = mock(NotificacaoStrategy.class);
        when(s1.pegarCanal()).thenReturn(null);

        Instance<NotificacaoStrategy> instance = instanceCom(s1);

        // EnumMap não aceita null key; deve lançar NPE (ou você pode escolher lançar sua exception)
        assertThrows(NullPointerException.class, () -> new ProcessadorNotificacao(instance));
    }

    @Test
    void deveLancarNpeSeMensagemVierComCanaisNulos() {
        NotificacaoStrategy email = mock(NotificacaoStrategy.class);
        when(email.pegarCanal()).thenReturn(CanalEntrega.EMAIL);

        ProcessadorNotificacao processor = new ProcessadorNotificacao(instanceCom(email));
        clearInvocations(email);

        // Se seu record permitir null (ex: veio de desserialização malformada), o for-each quebra.
        // Esse teste documenta o comportamento atual. Se quiser tratar, dá pra ajustar o production code.
        var msg = new br.com.protbike.records.BoletoNotificacaoMessage(
                "tenant-123",
                "proc-xyz-987",
                "20230001",
                "importacao.csv",
                "FECHAMENTO",
                null, // canais nulos
                BoletoTestHelper.createDefaultDestinatario(),
                BoletoTestHelper.createDefaultBoleto(),
                BoletoTestHelper.createDefaultMeta()
        );

        assertThrows(NullPointerException.class, () -> processor.processarEntrega(msg));
    }

    @Test
    void devePropagarExcecaoDaStrategySeVoceQuiserReprocessar() {
        // Esse teste só faz sentido se você decidir que exceção deve “subir” (pra retry/DLQ).
        // No seu código atual, você NÃO captura exceção. Então ela vai propagar mesmo.
        NotificacaoStrategy email = mock(NotificacaoStrategy.class);
        when(email.pegarCanal()).thenReturn(CanalEntrega.EMAIL);

        RuntimeException boom = new RuntimeException("falhou");
        doThrow(boom).when(email).enviarMensagem(any());

        ProcessadorNotificacao processor = new ProcessadorNotificacao(instanceCom(email));
        clearInvocations(email);

        var msg = BoletoTestHelper.builder()
                .withCanais(CanalEntrega.EMAIL)
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> processor.processarEntrega(msg));
        assertSame(boom, ex);
    }
}
