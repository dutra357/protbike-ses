//package br.com.protbike.strategy;
//
//import br.com.protbike.utils.BoletoNotificacaoMessageBuilder;
//import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
//import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
//import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
//import br.com.protbike.records.BoletoNotificacaoMessage;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import software.amazon.awssdk.services.sesv2.SesV2AsyncClient;
//import software.amazon.awssdk.services.sesv2.model.*;
//
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.CompletionException;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//
//@ExtendWith(MockitoExtension.class)
//class SimpleEmailStrategyTest {
//
//    @Mock
//    private SesV2AsyncClient sesClient;
//
//    private SimpleEmailStrategy strategy;
//
//    private BoletoNotificacaoMessage mensagemValida;
//
//    @BeforeEach
//    void setup() {
//        strategy = new SimpleEmailStrategy(sesClient);
//
//        mensagemValida = BoletoNotificacaoMessageBuilder.novo().build();
//    }
//
//    @Test
//    @DisplayName("Sucesso: Deve construir o email corretamente e retornar EnvioSucesso")
//    void deveRetornarSucessoQuandoSesAceitar() {
//        // GIVEN
//        SendEmailResponse mockResponse = SendEmailResponse.builder().messageId("msg-123").build();
//        when(sesClient.sendEmail(any(SendEmailRequest.class)))
//                .thenReturn(CompletableFuture.completedFuture(mockResponse));
//
//        // WHEN
//        ResultadoEnvio resultado = strategy.enviarMensagem(mensagemValida).join();
//
//        // THEN
//        assertTrue(resultado instanceof EnvioSucesso);
//        assertEquals(mensagemValida.numeroProtocolo(), resultado.protocolo());
//
//        // Verificação da Completude do Email (Campos do Request)
//        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
//        verify(sesClient).sendEmail(captor.capture());
//
//        SendEmailRequest requestSent = captor.getValue();
//        assertEquals("ses-observabilidade", requestSent.configurationSetName());
//        assertEquals(mensagemValida.destinatario().email(), requestSent.destination().toAddresses().get(0));
//        assertTrue(requestSent.content().simple().subject().data().contains(mensagemValida.meta().associacaoApelido().toUpperCase()));
//    }
//
//    @Test
//    @DisplayName("Caso 1: BadRequestException deve retornar EnvioFalhaNaoRetryavel (DLQ)")
//    void deveRetornarFalhaNaoRetryavelParaErroDeNegocio() {
//        // GIVEN
//        BadRequestException ex = BadRequestException.builder().message("Email inválido").build();
//        CompletableFuture<SendEmailResponse> future = new CompletableFuture<>();
//        future.completeExceptionally(ex);
//
//        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(future);
//
//        // WHEN
//        ResultadoEnvio resultado = strategy.enviarMensagem(mensagemValida).join();
//
//        // THEN
//        assertTrue(resultado instanceof EnvioFalhaNaoRetryavel);
//        assertFalse(resultado.retryavel());
//        assertTrue(resultado.motivo().contains("BadRequestException"));
//    }
//
//    @Test
//    @DisplayName("Caso 2: TooManyRequests deve disparar erro transiente")
//    void deveLancarExcecaoParaErroTransiente() {
//        // GIVEN
//        TooManyRequestsException awsEx = TooManyRequestsException.builder().message("Throttling").build();
//        CompletableFuture<SendEmailResponse> future = new CompletableFuture<>();
//        future.completeExceptionally(awsEx);
//
//        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(future);
//
//        // WHEN
//        CompletionException thrown = assertThrows(CompletionException.class,
//                () -> strategy.enviarMensagem(mensagemValida).join());
//
//        // THEN - Vamos buscar a causa raiz independentemente de quantos níveis existam
//        Throwable raiz = thrown;
//        while (raiz.getCause() != null) {
//            raiz = raiz.getCause();
//        }
//
//        // Imprime no console se falhar para você ver o que chegou de verdade
//        System.out.println("Causa raiz real encontrada: " + raiz.getClass().getName());
//
//        assertTrue(raiz instanceof TooManyRequestsException,
//                "A causa raiz deveria ser TooManyRequestsException, mas foi: " + raiz.getClass().getName());
//    }
//
//    @Test
//    @DisplayName("Caso 3: Erro 500 da AWS deve lançar RuntimeException (Retry)")
//    void deveDispararRetryParaErro5xxDaAmazon() {
//        // GIVEN
//        SesV2Exception aws500 = (SesV2Exception) SesV2Exception.builder()
//                .message("Internal Server Error")
//                .statusCode(500)
//                .build();
//
//        CompletableFuture<SendEmailResponse> future = new CompletableFuture<>();
//        future.completeExceptionally(aws500);
//
//        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(future);
//
//        // WHEN & THEN
//        CompletionException thrown = assertThrows(CompletionException.class,
//                () -> strategy.enviarMensagem(mensagemValida).join());
//
//        assertTrue(thrown.getCause() instanceof RuntimeException);
//    }
//
//    @Test
//    @DisplayName("Caso 4: NullPointerException (ou erros inesperados) deve lançar RuntimeException")
//    void deveLancarExcecaoParaErrosInesperadosDoJava() {
//        // GIVEN
//        NullPointerException npe = new NullPointerException("Erro de código");
//        CompletableFuture<SendEmailResponse> future = new CompletableFuture<>();
//        future.completeExceptionally(npe);
//
//        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(future);
//
//        // WHEN & THEN
//        assertThrows(CompletionException.class, () -> strategy.enviarMensagem(mensagemValida).join());
//    }
//}