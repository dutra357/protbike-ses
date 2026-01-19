package br.com.protbike.input;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.service.ProcessadorNotificacao;

import br.com.protbike.utils.SQSEventFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@QuarkusTest
class HandlerControllerTest {

    // Pega a classe real
    @Inject
    HandlerController lambdaHandler;

    // Mocka para a lógica de negócio
    @InjectMock
    ProcessadorNotificacao processorMock;

    String jsonPayload = """
        {
          "boletos": [
            {
              "tenant_id": "tenant-A",
              "protocolo": "PROTO-001",
              "canais": ["email"],
              "destinatario": { "nome": "Jose", "email": "z@z.com" },
              "boleto": { "nosso_numero": 100 },
              "meta": { "origem_sistema": "ERP" }
            },
            {
              "tenant_id": "tenant-A",
              "protocolo": "PROTO-002",
              "canais": ["whatsapp"],
              "destinatario": { "nome": "Maria", "telefone_whatsapp": "5511..." },
              "boleto": { "nosso_numero": 101 },
              "meta": { "origem_sistema": "ERP" }
            }
          ]
        }
    """;

    @Test
    void deveFazerParseCorretoEChamarProcessadorParaCadaBoleto() {
        // 1. Simula o evento SQS chegando na Lambda
        var event = SQSEventFactory.createEvent(jsonPayload);

        // 2. Executa o handler
        lambdaHandler.handleRequest(event, null);

        // 3. Verifica se o Parse funcionou e chamou o processador 2 vezes (pois há 2 boletos no JSON)
        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);

        verify(processorMock, times(2)).processarEntrega(captor.capture());

        // 4. Valida se os dados chegaram íntegros dentro do método
        var boletosCapturados = captor.getAllValues();

        assertEquals("PROTO-001", boletosCapturados.get(0).numeroProtocolo());
        assertEquals("PROTO-002", boletosCapturados.get(1).numeroProtocolo());
    }
}