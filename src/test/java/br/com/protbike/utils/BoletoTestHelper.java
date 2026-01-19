package br.com.protbike.utils;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.BoletoNotificacaoMessage.Boleto;
import br.com.protbike.records.BoletoNotificacaoMessage.Destinatario;
import br.com.protbike.records.BoletoNotificacaoMessage.Meta;
import br.com.protbike.records.BoletoNotificacaoMessage.Pix;
import br.com.protbike.records.enuns.CanalEntrega;

import java.time.Instant;
import java.util.*;

public class BoletoTestHelper {

    public static BoletoNotificacaoMessage createDefaultMessage() {
        return builder().build();
    }

    public static BoletoMessageBuilder builder() {
        return new BoletoMessageBuilder();
    }

    // BUILDER
    public static class BoletoMessageBuilder {
        private String tenantId = "tenant-123";
        private String processamentoId = "proc-xyz-987";
        private String numeroProtocolo = "20230001";
        private String origemCsv = "importacao_janeiro.csv";
        private String tipoEvento = "FECHAMENTO";
        private Set<CanalEntrega> canais = new HashSet<>();
        private Destinatario destinatario = createDefaultDestinatario();
        private Boleto boleto = createDefaultBoleto();
        private Meta meta = createDefaultMeta();

        // Métodos Fluentes
        public BoletoMessageBuilder withTenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public BoletoMessageBuilder withProcessamentoId(String processamentoId) {
            this.processamentoId = processamentoId;
            return this;
        }

        public BoletoMessageBuilder withProtocolo(String numeroProtocolo) {
            this.numeroProtocolo = numeroProtocolo;
            return this;
        }

        public BoletoMessageBuilder withCanais(CanalEntrega... canais) {
            this.canais = EnumSet.copyOf(Arrays.asList(canais));
            return this;
        }

        public BoletoMessageBuilder withDestinatario(Destinatario destinatario) {
            this.destinatario = destinatario;
            return this;
        }

        // Mudar só o email do destinatário rapidamente
        public BoletoMessageBuilder withDestinatarioEmail(String email) {
            this.destinatario = new Destinatario(
                    this.destinatario.nome(),
                    this.destinatario.cpf(),
                    this.destinatario.telefoneWhatsapp(),
                    email
            );
            return this;
        }

        // Mudar só o whats
        public BoletoMessageBuilder withDestinatarioWhatsapp(String whatsapp) {
            this.destinatario = new Destinatario(
                    this.destinatario.nome(),
                    this.destinatario.cpf(),
                    whatsapp,
                    this.destinatario.email()
            );
            return this;
        }

        public BoletoMessageBuilder withBoleto(Boleto boleto) {
            this.boleto = boleto;
            return this;
        }

        public BoletoMessageBuilder withMeta(Meta meta) {
            this.meta = meta;
            return this;
        }

        public BoletoNotificacaoMessage build() {
            return new BoletoNotificacaoMessage(
                    tenantId, processamentoId, numeroProtocolo, origemCsv,
                    tipoEvento, canais, destinatario, boleto, meta
            );
        }
    }

    // AUXILIARES

    public static Destinatario createDefaultDestinatario() {
        return new Destinatario(
                "João da Silva",
                "123.456.789-00",
                "5511999999999",
                "joao@email.com"
        );
    }

    public static Boleto createDefaultBoleto() {
        return new Boleto(
                123456,
                "01/2024",
                "2024-01-10",
                "2023-12-20",
                "34191.79001 01043.510047 91020.150008 5 95950000015000",
                "34191.79001...",
                "150.00",
                "EM ABERTO",
                "https://boletos.com/123",
                new Pix("00020126580014br.gov.bcb.pix0136123e4567-e89b-12d3-a456-426614174000")
        );
    }

    public static Meta createDefaultMeta() {
        return new Meta(
                Instant.now(),
                "SISTEMA_LEGADO",
                "admin@protbike.com.br",
                "Associação CLIENT",
                "Associação"
        );
    }
}