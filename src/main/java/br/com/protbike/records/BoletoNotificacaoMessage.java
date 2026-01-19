package br.com.protbike.records;

import br.com.protbike.records.enuns.CanalEntrega;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;


import java.time.Instant;
import java.util.Set;

@RegisterForReflection
public record BoletoNotificacaoMessage(

        @JsonProperty("tenant_id")
        String tenantId,

        @JsonProperty("processamento_id")
        String processamentoId,

        @JsonProperty("protocolo")
        String numeroProtocolo,

        @JsonProperty("origem_csv")
        String origemCsv,

        // Tipo do evento (ex: BOLETO_FECHAMENTO)
        @JsonProperty("tipo_evento")
        String tipoEvento,

        // Set de canais de entrega (ex: ["WHATSAPP"])
        @JsonProperty("canais")
        Set<CanalEntrega> canais,

        // Para quem será enviada a mensagem
        @JsonProperty("destinatario")
        Destinatario destinatario,

        // Dados estruturados do boleto
        @JsonProperty("boleto")
        Boleto boleto,

        // Metadados para auditoria e rastreabilidade
        @JsonProperty("meta")
        Meta meta

) {
        @RegisterForReflection
        public record Destinatario(

                @JsonProperty("nome")
                String nome,

                @JsonProperty("cpf")
                String cpf,

                @JsonProperty("telefone_whatsapp")
                String telefoneWhatsapp,

                @JsonProperty("email")
                String email

        ) {}

        @RegisterForReflection
        public record Boleto(

                @JsonProperty("nosso_numero")
                Integer nossoNumero,

                @JsonProperty("mes_referente")
                String mesReferente,

                @JsonProperty("data_vencimento")
                String dataVencimento,

                @JsonProperty("data_emissao")
                String dataEmissao,

                @JsonProperty("linha_digitavel")
                String linhaDigitavel,

                @JsonProperty("linha_digitavel_atual")
                String linhaDigitavelAtual,

                @JsonProperty("valor_boleto")
                String valorBoleto,

                @JsonProperty("descricao_situacao_boleto")
                String descricaoSituacaoBoleto,

                @JsonProperty("link_boleto")
                String linkBoleto,

                @JsonProperty("pix")
                Pix pix

        ) {}

        @RegisterForReflection
        public record Pix(
                @JsonProperty("copia_cola")
                String copiaCola
        ) {}

        @RegisterForReflection
        public record Meta(

                @JsonProperty("criado_em")
                Instant criadoEm,

                @JsonProperty("origem_sistema")
                String origemSistema,

                @JsonProperty("adm_email")
                String admEmail,

                @JsonProperty("associacao_clinte")
                String associacaoCliente,

                @JsonProperty("associacao_apelido")
                String associacaoApelido
        ) {}
}
