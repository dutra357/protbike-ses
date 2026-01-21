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
        @JsonProperty("protocolo") String numeroProtocolo,
        @JsonProperty("tipo_evento") String tipoEvento,
        @JsonProperty("canais") Set<CanalEntrega> canais,
        @JsonProperty("destinatario") Destinatario destinatario,
        @JsonProperty("boleto") Boleto boleto,
        @JsonProperty("meta") Meta meta
) {
        public BoletoNotificacaoMessage {
                requireNotBlank(numeroProtocolo, "protocolo");
                requireNotBlank(tipoEvento, "tipo_evento");
                requireNotNullAndNotEmpty(canais, "canais");

                if (canais.stream().anyMatch(Objects::isNull)) {
                        throw new CampoEntradaInvalidoException("From SQS: Mensagem final possui canais NULL.");
                }

                requireNotNull(destinatario, "destinatario");
                requireNotNull(boleto, "boleto");
                requireNotNull(meta, "meta");

                // Validação de consistência de canal
                if (canais.contains(CanalEntrega.EMAIL) && (destinatario.email() == null || destinatario.email().isBlank())) {
                        throw new CampoEntradaInvalidoException("From SQS: Canal EMAIL exige destinatario.email preenchido");
                }
        }

        @RegisterForReflection
        public record Destinatario(
                String nome,
                String cpf,
                @JsonProperty("telefone_whatsapp") String telefoneWhatsapp,
                String email
        ) {
                public Destinatario {
                        requireNotBlank(nome, "destinatario.nome");
                        requireNotBlank(cpf, "destinatario.cpf");
                        requireNotBlank(telefoneWhatsapp, "destinatario.telefone_whatsapp");
                        requireNotBlank(email, "destinatario.email");
                }
        }

        @RegisterForReflection
        public record Boleto(
                @JsonProperty("nosso_numero") Integer nossoNumero,
                @JsonProperty("mes_referente") String mesReferente,
                @JsonProperty("data_vencimento") String dataVencimento,
                @JsonProperty("data_emissao") String dataEmissao,
                @JsonProperty("linha_digitavel") String linhaDigitavel,
                @JsonProperty("linha_digitavel_atual") String linhaDigitavelAtual,
                @JsonProperty("valor_boleto") String valorBoleto,
                @JsonProperty("descricao_situacao_boleto") String descricaoSituacaoBoleto,
                @JsonProperty("link_boleto") String linkBoleto,
                Pix pix,
                VeiculoResumo veiculo
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

                        // O PIX pode ser nulo se o destino estiver preparado,
                        // mas conforme solicitado, manteremos a validação se deve ser Not Null
                        requireNotNull(pix, "boleto.pix");
                }
        }

        @RegisterForReflection
        public record Pix(@JsonProperty("copia_cola") String copiaCola) {
                public Pix {
                        requireNotBlank(copiaCola, "pix.copia_cola");
                }
        }

        @RegisterForReflection
        public record VeiculoResumo(String placa, String modelo) {
                public VeiculoResumo {
                        requireNotBlank(placa, "veiculo.placa");
                        requireNotBlank(modelo, "veiculo.modelo");
                }
        }
        @RegisterForReflection
        public record Meta(
                @JsonProperty("tenant_id") String tenantId,
                @JsonProperty("criado_em") Instant criadoEm,
                @JsonProperty("origem_sistema") String origemSistema,
                @JsonProperty("origem_csv") String origemCsv,
                @JsonProperty("adm_email") String admEmail,
                @JsonProperty("processamento_id") String csvProcessamentoId,
                @JsonProperty("associacao_clinte") String associacaoCliente,
                @JsonProperty("associacao_apelido") String associacaoApelido
        ) {
                public Meta {
                        requireNotBlank(tenantId, "tenant_id");
                        requireNotNull(criadoEm, "meta.criado_em");
                        requireNotBlank(origemSistema, "meta.origem_sistema");
                        requireNotBlank(origemCsv, "origem_csv");
                        requireNotBlank(admEmail, "meta.adm_email");
                        requireNotBlank(csvProcessamentoId, "processamento_id");
                        requireNotBlank(associacaoCliente, "meta.associacao_clinte");
                        requireNotBlank(associacaoApelido, "meta.associacao_apelido");
                }
        }

        private static void requireNotNull(Object value, String field) {
                if (value == null) throw new CampoEntradaInvalidoException("From SQS: " + field + " não pode ser null");
        }

        private static void requireNotBlank(String value, String field) {
                if (value == null || value.trim().isEmpty()) {
                        throw new CampoEntradaInvalidoException("From SQS: " + field + " não pode ser null/vazio");
                }
        }

        private static <T> void requireNotNullAndNotEmpty(Set<T> value, String field) {
                if (value == null || value.isEmpty()) {
                        throw new CampoEntradaInvalidoException("From SQS: " + field + " não pode ser null/vazio");
                }
        }
}