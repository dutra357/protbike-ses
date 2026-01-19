package br.com.protbike.records;

import br.com.protbike.exceptions.CampoEntradaInvalidoException;
import br.com.protbike.records.enuns.CanalEntrega;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.Objects;
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

        @JsonProperty("tipo_evento")
        String tipoEvento,

        @JsonProperty("canais")
        Set<CanalEntrega> canais,

        @JsonProperty("destinatario")
        Destinatario destinatario,

        @JsonProperty("boleto")
        Boleto boleto,

        @JsonProperty("meta")
        Meta meta

) {
        public BoletoNotificacaoMessage {
                requireNotBlank(tenantId, "tenant_id");
                requireNotBlank(processamentoId, "processamento_id");
                requireNotBlank(numeroProtocolo, "protocolo");
                requireNotBlank(origemCsv, "origem_csv");
                requireNotBlank(tipoEvento, "tipo_evento");

                requireNotNullAndNotEmpty(canais, "canais");
                // evita null dentro do set (se vier [null] no JSON)
                if (canais.stream().anyMatch(Objects::isNull)) {
                        throw new IllegalArgumentException("canais contém valor nulo");
                }

                requireNotNull(destinatario, "destinatario");
                requireNotNull(boleto, "boleto");
                requireNotNull(meta, "meta");
        }

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

        ) {
                public Destinatario {
                        requireNotBlank(nome, "destinatario.nome");

                        // Evitar nulo/vazio nos principais identificadores e canais de contato
                        requireNotBlank(cpf, "destinatario.cpf");
                        requireNotBlank(telefoneWhatsapp, "destinatario.telefone_whatsapp");
                        requireNotBlank(email, "destinatario.email");
                }
        }

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

        ) {
                public Boleto {
                        requireNotNull(nossoNumero, "boleto.nosso_numero");
                        requireNotBlank(mesReferente, "boleto.mes_referente");
                        requireNotBlank(dataVencimento, "boleto.data_vencimento");
                        requireNotBlank(dataEmissao, "boleto.data_emissao");
                        requireNotBlank(linhaDigitavel, "boleto.linha_digitavel");
                        requireNotBlank(linhaDigitavelAtual, "boleto.linha_digitavel_atual");
                        requireNotBlank(valorBoleto, "boleto.valor_boleto");
                        requireNotBlank(descricaoSituacaoBoleto, "boleto.descricao_situacao_boleto");
                        requireNotBlank(linkBoleto, "boleto.link_boleto");

                        requireNotNull(pix, "boleto.pix");
                }
        }

        @RegisterForReflection
        public record Pix(
                @JsonProperty("copia_cola")
                String copiaCola
        ) {
                public Pix {
                        requireNotBlank(copiaCola, "pix.copia_cola");
                }
        }

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
        ) {
                public Meta {
                        requireNotNull(criadoEm, "meta.criado_em");
                        requireNotBlank(origemSistema, "meta.origem_sistema");
                        requireNotBlank(admEmail, "meta.adm_email");
                        requireNotBlank(associacaoCliente, "meta.associacao_clinte");
                        requireNotBlank(associacaoApelido, "meta.associacao_apelido");
                }
        }

        private static void requireNotNull(Object value, String field) {
                if (value == null) {
                        throw new CampoEntradaInvalidoException(field + " não pode ser null");
                }
        }

        private static void requireNotBlank(String value, String field) {
                if (value == null || value.trim().isEmpty()) {
                        throw new CampoEntradaInvalidoException(field + " não pode ser null/vazio");
                }
        }

        private static <T> void requireNotNullAndNotEmpty(Set<T> value, String field) {
                if (value == null || value.isEmpty()) {
                        throw new CampoEntradaInvalidoException(field + " não pode ser null/vazio");
                }
        }
}