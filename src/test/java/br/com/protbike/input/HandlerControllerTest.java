//package br.com.protbike.input;
//
//import br.com.protbike.utils.BoletoNotificacaoMessageBuilder;
//import br.com.protbike.config.DlqPublisher;
//import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
//import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
//import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
//import br.com.protbike.metrics.Metricas;
//import br.com.protbike.records.BoletoNotificacaoMessage;
//import br.com.protbike.records.BoletoNotificacaoWrapper;
//import br.com.protbike.service.ProcessadorNotificacao;
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
//import com.amazonaws.services.lambda.runtime.events.SQSEvent;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.quarkus.test.InjectMock;
//import io.quarkus.test.junit.QuarkusTest;
//import jakarta.inject.Inject;
//import org.jboss.logging.MDC;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Captor;
//import org.mockito.Mock;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
//@QuarkusTest
//@DisplayName("Testes do HandlerController com Contexto CDI")
//class HandlerControllerTest {
//
//    @Inject
//    HandlerController handlerController;
//
//    @InjectMock
//    ObjectMapper objectMapper;
//
//    @InjectMock
//    ProcessadorNotificacao processador;
//
//    @InjectMock
//    Metricas metricas;
//
//    @InjectMock
//    DlqPublisher dlqPublisher;
//
//    @Mock
//    Context context;
//
//    @Captor
//    private ArgumentCaptor<List<BoletoNotificacaoMessage>> dlqCaptor;
//
//    @BeforeEach
//    void setUp() {
//        when(context.getAwsRequestId()).thenReturn("test-request-id");
//        MDC.clear();
//    }
//
//    @AfterEach
//    void tearDown() {
//        MDC.clear();
//    }
//
//    @Test
//    @DisplayName("Sucesso Total: Deve processar lote e retornar lista de falhas vazia")
//    void handleRequest_SucessoTotal() throws Exception {
//        BoletoNotificacaoMessage b1 = BoletoNotificacaoMessageBuilder.novo().comProtocolo("P1").build();
//        BoletoNotificacaoWrapper wrapper = new BoletoNotificacaoWrapper("lote-1", List.of(b1));
//        SQSEvent event = criarSqsEvent("msg-01", "corpo");
//
//        when(objectMapper.readValue(anyString(), eq(BoletoNotificacaoWrapper.class))).thenReturn(wrapper);
//        when(processador.processarEntrega(any())).thenReturn(List.of(new EnvioSucesso("P1")));
//
//        SQSBatchResponse response = handlerController.handleRequest(event, context);
//
//        assertTrue(response.getBatchItemFailures().isEmpty());
//        verify(metricas).reset();
//    }
//
//    @Test
//    @DisplayName("Falha Temporária: Deve marcar BatchItemFailure para reprocessamento SQS")
//    void handleRequest_FalhaRetryavelReportaAoSqs() throws Exception {
//        BoletoNotificacaoMessage b1 = BoletoNotificacaoMessageBuilder.novo().build();
//        BoletoNotificacaoWrapper wrapper = new BoletoNotificacaoWrapper("lote-1", List.of(b1));
//        SQSEvent event = criarSqsEvent("msg-retry", "body");
//
//        ResultadoEnvio resultadoRetry = mock(ResultadoEnvio.class);
//        when(resultadoRetry.sucesso()).thenReturn(false);
//        when(resultadoRetry.retryavel()).thenReturn(true);
//
//        when(objectMapper.readValue(anyString(), eq(BoletoNotificacaoWrapper.class))).thenReturn(wrapper);
//        when(processador.processarEntrega(any())).thenReturn(List.of(resultadoRetry));
//
//        SQSBatchResponse response = handlerController.handleRequest(event, context);
//
//        assertEquals(1, response.getBatchItemFailures().size());
//        assertEquals("msg-retry", response.getBatchItemFailures().get(0).getItemIdentifier());
//    }
//
//    @Test
//    @DisplayName("Falha de Negócio: Deve enviar para DLQ e não falhar o lote SQS")
//    void handleRequest_FalhaNegocioVaiParaDlq() throws Exception {
//        BoletoNotificacaoMessage b1 = BoletoNotificacaoMessageBuilder.novo().comProtocolo("P-FAIL").build();
//        BoletoNotificacaoWrapper wrapper = new BoletoNotificacaoWrapper("lote-1", List.of(b1));
//        SQSEvent event = criarSqsEvent("msg-01", "body");
//
//        when(objectMapper.readValue(anyString(), eq(BoletoNotificacaoWrapper.class))).thenReturn(wrapper);
//        when(processador.processarEntrega(b1)).thenReturn(List.of(new EnvioFalhaNaoRetryavel("P-FAIL", "Erro fatal")));
//
//        SQSBatchResponse response = handlerController.handleRequest(event, context);
//
//        assertTrue(response.getBatchItemFailures().isEmpty());
//        verify(dlqPublisher).enviar(any());
//    }
//
//    @Test
//    @DisplayName("Erro de Parse: Deve falhar o item do lote se o JSON for inválido")
//    void handleRequest_JsonInvalido() throws Exception {
//        SQSEvent event = criarSqsEvent("msg-bad-json", "invalid");
//        when(objectMapper.readValue(anyString(), eq(BoletoNotificacaoWrapper.class)))
//                .thenThrow(mock(JsonProcessingException.class));
//
//        SQSBatchResponse response = handlerController.handleRequest(event, context);
//
//        assertEquals(1, response.getBatchItemFailures().size());
//    }
//
//    @Test
//    @DisplayName("Proteção contra NPE: Deve ignorar mensagens com corpo vazio ou nulo")
//    void handleRequest_CorpoVazio() throws Exception {
//        BoletoNotificacaoWrapper wrapperNulo = new BoletoNotificacaoWrapper(null, null);
//        SQSEvent event = criarSqsEvent("msg-empty", "{}");
//        when(objectMapper.readValue(anyString(), eq(BoletoNotificacaoWrapper.class))).thenReturn(wrapperNulo);
//
//        SQSBatchResponse response = handlerController.handleRequest(event, context);
//
//        assertTrue(response.getBatchItemFailures().isEmpty());
//    }
//
//    private SQSEvent criarSqsEvent(String messageId, String body) {
//        SQSEvent.SQSMessage msg = new SQSEvent.SQSMessage();
//        msg.setMessageId(messageId);
//        msg.setBody(body);
//        SQSEvent event = new SQSEvent();
//        event.setRecords(List.of(msg));
//        return event;
//    }
//}