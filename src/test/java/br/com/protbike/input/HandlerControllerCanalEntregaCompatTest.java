package br.com.protbike.input;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
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

    private void execSingleBoletoWithCanais(String canaisJson) {
        String payload = """
            {
              "boletos": [
                {
                  "tenant_id": "tenant-A",
                  "processamento_id": "proc-123",
                  "protocolo": "PROTO-ENUM-001",
                  "origem_csv": "importacao.csv",
                  "tipo_evento": "BOLETO_FECHAMENTO",
                  "canais": %s,
                  "destinatario": {
                    "nome": "Jose da Silva",
                    "cpf": "123.456.789-00",
                    "telefone_whatsapp": "5511999999999",
                    "email": "jose@exemplo.com"
                  },
                  "boleto": {
                    "nosso_numero": 100,
                    "mes_referente": "01/2026",
                    "data_vencimento": "2026-01-31",
                    "data_emissao": "2026-01-01",
                    "linha_digitavel": "34191.79001 01043.510047 91020.150008 5 95950000015000",
                    "linha_digitavel_atual": "34191.79001 01043.510047 91020.150008 5 95950000015000",
                    "valor_boleto": "150.00",
                    "descricao_situacao_boleto": "EM ABERTO",
                    "link_boleto": "https://exemplo.com/boleto/100",
                    "pix": { "copia_cola": "00020126580014br.gov.bcb.pix0136abc..." }
                  },
                  "meta": {
                    "criado_em": "2026-01-19T00:00:00Z",
                    "origem_sistema": "ERP",
                    "adm_email": "admin@protbike.com.br",
                    "associacao_clinte": "Associacao X",
                    "associacao_apelido": "ASSOC-X"
                  }
                }
              ]
            }
        """.formatted(canaisJson);

        var event = SQSEventFactory.createEvent(payload);
        lambdaHandler.handleRequest(event, null);
    }

    private BoletoNotificacaoMessage captureSingleMessage() {
        ArgumentCaptor<BoletoNotificacaoMessage> captor = ArgumentCaptor.forClass(BoletoNotificacaoMessage.class);
        verify(processorMock, times(1)).processarEntrega(captor.capture());
        return captor.getValue();
    }

    // ========= Cenários OK (case/trim/aliases) =========

    @Test
    void deveAceitarEmailMinusculo() {
        execSingleBoletoWithCanais("[\"email\"]");
        var msg = captureSingleMessage();
        assertTrue(msg.canais().contains(CanalEntrega.EMAIL));
    }

    @Test
    void deveAceitarEmailMaiusculo() {
        execSingleBoletoWithCanais("[\"EMAIL\"]");
        var msg = captureSingleMessage();
        assertTrue(msg.canais().contains(CanalEntrega.EMAIL));
    }

    @Test
    void deveAceitarEmailMisto() {
        execSingleBoletoWithCanais("[\"eMaIl\"]");
        var msg = captureSingleMessage();
        assertTrue(msg.canais().contains(CanalEntrega.EMAIL));
    }

    @Test
    void deveAceitarWhatsappComEspacos() {
        execSingleBoletoWithCanais("[\"   whatsapp   \"]");
        var msg = captureSingleMessage();
        assertTrue(msg.canais().contains(CanalEntrega.WHATSAPP));
    }

    @Test
    void deveAceitarAliasDeWhatsapp() {
        execSingleBoletoWithCanais("[\"zap\"]");
        var msg = captureSingleMessage();
        assertTrue(msg.canais().contains(CanalEntrega.WHATSAPP));
    }

    @Test
    void deveAceitarVariacaoEmailComHifen() {
        execSingleBoletoWithCanais("[\"E-mail\"]");
        var msg = captureSingleMessage();
        assertTrue(msg.canais().contains(CanalEntrega.EMAIL));
    }

// ========= Cenários inválidos =========

    @Test
    void deveFalharQuandoCanalForTextoInvalido() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> execSingleBoletoWithCanais("[\"fax\"]"));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCanalForStringVazia() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> execSingleBoletoWithCanais("[\"\"]"));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCanalForWhitespace() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> execSingleBoletoWithCanais("[\"   \"]"));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCanalForNullDentroDoArray() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> execSingleBoletoWithCanais("[null]"));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCampoCanaisForNull() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> execSingleBoletoWithCanais("null"));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCampoCanaisForArrayVazio() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> execSingleBoletoWithCanais("[]"));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }

    @Test
    void deveFalharQuandoCampoCanaisForAusente() {
        // JSON válido, mas SEM "canais". Como o record valida (canais não pode ser null/vazio),
        // isso deve falhar durante parse/construção do record.
        String payload = """
            {
              "boletos": [
                {
                  "tenant_id": "tenant-A",
                  "processamento_id": "proc-123",
                  "protocolo": "PROTO-SEM-CANAIS",
                  "origem_csv": "importacao.csv",
                  "tipo_evento": "BOLETO_FECHAMENTO",
                  "destinatario": {
                    "nome": "Jose da Silva",
                    "cpf": "123.456.789-00",
                    "telefone_whatsapp": "5511999999999",
                    "email": "jose@exemplo.com"
                  },
                  "boleto": {
                    "nosso_numero": 100,
                    "mes_referente": "01/2026",
                    "data_vencimento": "2026-01-31",
                    "data_emissao": "2026-01-01",
                    "linha_digitavel": "34191.79001 01043.510047 91020.150008 5 95950000015000",
                    "linha_digitavel_atual": "34191.79001 01043.510047 91020.150008 5 95950000015000",
                    "valor_boleto": "150.00",
                    "descricao_situacao_boleto": "EM ABERTO",
                    "link_boleto": "https://exemplo.com/boleto/100",
                    "pix": { "copia_cola": "00020126580014br.gov.bcb.pix0136abc..." }
                  },
                  "meta": {
                    "criado_em": "2026-01-19T00:00:00Z",
                    "origem_sistema": "ERP",
                    "adm_email": "admin@protbike.com.br",
                    "associacao_clinte": "Associacao X",
                    "associacao_apelido": "ASSOC-X"
                  }
                }
              ]
            }
        """;

        var event = SQSEventFactory.createEvent(payload);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> lambdaHandler.handleRequest(event, null));

        assertEquals("Falha no processamento do batch", ex.getMessage());
        verifyNoInteractions(processorMock);
    }
}
