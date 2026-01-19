package br.com.protbike.input;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.service.ProcessadorNotificacao;
import br.com.protbike.utils.SQSEventFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class HandlerControllerCanalEntregaCompatTest {

    @Inject
    HandlerController lambdaHandler;

    @InjectMock
    ProcessadorNotificacao processorMock;

    private void executeSingleBoleto(String canaisJsonArray) {
        String payload = """
            {
              "boletos": [
                {
                  "tenant_id": "tenant-A",
                  "protocolo": "PROTO-ENUM-001",
                  "canais": %s,
                  "destinatario": { "nome": "Jose", "email": "z@z.com" },
                  "boleto": { "nosso_numero": 100 },
                  "meta": { "origem_sistema": "ERP" }
                }
              ]
            }
        """.formatted(canaisJsonArray);

        var event = SQSEventFactory.createEvent(payload);
        lambdaHandler.handleRequest(event, null);
    }

    @Test
    void deveAceitarCanalMinusculo() {
        executeSingleBoleto("""["email"]""");

        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);
        verify(processorMock, times(1)).processarEntrega(captor.capture());

        assertTrue(captor.getValue().canais().contains(br.com.protbike.records.enuns.CanalEntrega.EMAIL));
    }

    @Test
    void deveAceitarCanalMaiusculo() {
        executeSingleBoleto("""["EMAIL"]""");

        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);
        verify(processorMock, times(1)).processarEntrega(captor.capture());

        assertTrue(captor.getValue().canais().contains(br.com.protbike.records.enuns.CanalEntrega.EMAIL));
    }

    @Test
    void deveAceitarCanalMisto() {
        executeSingleBoleto("""["eMaIl"]""");

        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);
        verify(processorMock, times(1)).processarEntrega(captor.capture());

        assertTrue(captor.getValue().canais().contains(br.com.protbike.records.enuns.CanalEntrega.EMAIL));
    }

    @Test
    void deveAceitarCanalComEspacos() {
        executeSingleBoleto("""["  whatsapp  "]""");

        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);
        verify(processorMock, times(1)).processarEntrega(captor.capture());

        assertTrue(captor.getValue().canais().contains(br.com.protbike.records.enuns.CanalEntrega.WHATSAPP));
    }

    @Test
    void deveAceitarMultiplosCanaisComVariacoes() {
        executeSingleBoleto("""["EMAIL", " whatsapp ", "sMs"]""");

        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);
        verify(processorMock, times(1)).processarEntrega(captor.capture());

        var canais = captor.getValue().canais();
        assertTrue(canais.contains(br.com.protbike.records.enuns.CanalEntrega.EMAIL));
        assertTrue(canais.contains(br.com.protbike.records.enuns.CanalEntrega.WHATSAPP));
        assertTrue(canais.contains(br.com.protbike.records.enuns.CanalEntrega.SMS));
    }

    @Test
    void deveFalharQuandoCanalForTextoInvalido() {
        // Se o enum estiver com @JsonCreator lançando IllegalArgumentException,
        // o handler deve falhar antes de chamar o processor.
        assertThrows(RuntimeException.class, () -> executeSingleBoleto("""["fax"]"""));
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCanalForStringVazia() {
        assertThrows(RuntimeException.class, () -> executeSingleBoleto("""[""]"""));
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCanalForWhitespace() {
        assertThrows(RuntimeException.class, () -> executeSingleBoleto("""["   "]"""));
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCanalForNull() {
        assertThrows(RuntimeException.class, () -> executeSingleBoleto("""[null]"""));
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCampoCanaisForNull() {
        // aqui "canais": null (em vez de array)
        assertThrows(RuntimeException.class, () -> executeSingleBoleto("null"));
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCampoCanaisForAusente() {
        // monta payload SEM o campo "canais"
        String payload = """
            {
              "boletos": [
                {
                  "tenant_id": "tenant-A",
                  "protocolo": "PROTO-ENUM-SEM-CANAIS",
                  "destinatario": { "nome": "Jose", "email": "z@z.com" },
                  "boleto": { "nosso_numero": 100 },
                  "meta": { "origem_sistema": "ERP" }
                }
              ]
            }
        """;

        var event = SQSEventFactory.createEvent(payload);

        // Dependendo de como seu record/Jackson está configurado:
        // - pode virar null
        // - pode virar Set vazio
        // - ou pode falhar
        //
        // Aqui eu “documento” o comportamento esperado mais seguro: falhar (contrato obrigatório).
        // Se no seu projeto você prefere tratar como vazio, troque o assert para "doesNotThrow" e valide Set vazio.
        assertThrows(RuntimeException.class, () -> lambdaHandler.handleRequest(event, null));
        verifyNoInteractions(processorMock);
    }
}