package br.com.protbike.records;

import br.com.protbike.utils.BoletoNotificacaoMessageBuilder;
import br.com.protbike.exceptions.CampoEntradaInvalidoException;
import br.com.protbike.records.BoletoNotificacaoMessage.*;
import br.com.protbike.records.enuns.CanalEntrega;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BoletoNotificacaoMessageTest {

    @Test
    @DisplayName("Deve criar o record com sucesso quando todos os campos estão presentes e válidos")
    void deveCriarObjetoCompleto() {
        assertDoesNotThrow(() -> BoletoNotificacaoMessageBuilder.novo().build());
    }

    @Nested
    @DisplayName("Validações de Dados Pessoais (Destinatario)")
    class DestinatarioTests {

        @Test
        @DisplayName("Deve falhar se qualquer campo do destinatário for nulo ou vazio")
        void deveFalharCamposPessoaisVazios() {
            // Testando o construtor interno do Destinatario diretamente
            assertThrows(CampoEntradaInvalidoException.class, () ->
                    new Destinatario(null, "123", "123", "a@a.com"));

            assertThrows(CampoEntradaInvalidoException.class, () ->
                    new Destinatario("Nome", "", "123", "a@a.com"));
        }

        @Test
        @DisplayName("O próprio Record Destinatario deve impedir e-mail vazio")
        void deveFalharCriacaoDestinatarioSemEmail() {
            // O erro que você recebeu veio daqui:
            assertThrows(CampoEntradaInvalidoException.class, () ->
                            new Destinatario("Nome", "123", "123", "  "),
                    "O record Destinatario deve validar seus próprios campos no construtor"
            );
        }
    }

    @Nested
    @DisplayName("Validações de Dados Financeiros (Boleto)")
    class BoletoTests {

        @Test
        @DisplayName("Deve falhar se dados críticos do boleto estiverem ausentes")
        void deveFalharBoletoIncompleto() {
            // Nosso Numero nulo
            assertThrows(CampoEntradaInvalidoException.class, () ->
                    new Boleto(null, "Jan", "2026", "2026", "line", "line", "10", "OK", "link", null, null));
        }

        @Test
        @DisplayName("Deve falhar se o PIX for nulo (conforme regra de negócio atual)")
        void deveFalharSemPix() {
            BoletoNotificacaoMessageBuilder builder = BoletoNotificacaoMessageBuilder.novo();
            // Simular um boleto sem PIX manualmente se o builder não permitir
            assertThrows(CampoEntradaInvalidoException.class, () ->
                    new Boleto(1, "Mês", "Data", "Data", "LD", "LD", "100", "SIT", "LINK", null, null));
        }
    }

    @Nested
    @DisplayName("Validações de Infraestrutura (Canais e Meta)")
    class InfraTests {

        @Test
        @DisplayName("Deve falhar se a lista de canais estiver vazia ou com elementos nulos")
        void deveFalharCanaisInvalidos() {
            // Lista vazia
            assertThrows(CampoEntradaInvalidoException.class, () ->
                    BoletoNotificacaoMessageBuilder.novo().comCanais().build());

            // Lista com elemento null (usando HashSet para permitir null)
            Set<CanalEntrega> canaisComNull = new HashSet<>();
            canaisComNull.add(null);

            assertThrows(CampoEntradaInvalidoException.class, () ->
                    new BoletoNotificacaoMessage("P1", "E1", canaisComNull, null, null, null));
        }

        @Test
        @DisplayName("Deve falhar se metadados de rastreabilidade (Tenant/Processamento) forem nulos")
        void deveFalharMetaIncompleta() {
            assertThrows(CampoEntradaInvalidoException.class, () ->
                    new Meta(null, null, null, null, null, null, null, null));
        }
    }
}