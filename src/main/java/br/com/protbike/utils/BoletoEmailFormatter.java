package br.com.protbike.utils;

import br.com.protbike.records.BoletoNotificacaoMessage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class BoletoEmailFormatter {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BoletoEmailFormatter() {}

    public static String formatarAssunto(BoletoNotificacaoMessage boletoNotificacaoMessage) {
        String ref = safe(boletoNotificacaoMessage.boleto() != null ? boletoNotificacaoMessage.boleto().mesReferente() : null);
        String status = safe(boletoNotificacaoMessage.boleto() != null ? boletoNotificacaoMessage.boleto().descricaoSituacaoBoleto() : null);
        if (ref.isBlank()) ref = "N/D";
        return "PROTBIKE | Boleto | Ref. " + ref + " | Situação: " + status;
    }

    public static String formatarCorpoEmail(BoletoNotificacaoMessage msg) {
        Objects.requireNonNull(msg, "msg is required");

        var destinatario = msg.destinatario();
        var boleto = msg.boleto();
        var meta = msg.meta();

        String nome = safe(destinatario != null ? destinatario.nome() : null);
        String nossoNumero = boleto != null && boleto.nossoNumero() != null ? String.valueOf(boleto.nossoNumero()) : "N/D";
        String referencia = safe(boleto != null ? boleto.mesReferente() : null);
        String status = safe(boleto != null ? boleto.descricaoSituacaoBoleto() : null);

        String venc = formatDateBr(boleto != null ? boleto.dataVencimento() : null);
        String valor = formatMoneyBr(boleto != null ? boleto.valorBoleto() : null);

        String linha = firstNonBlank(
                boleto != null ? boleto.linhaDigitavelAtual() : null,
                boleto != null ? boleto.linhaDigitavel() : null
        );

        String link = safe(boleto != null ? boleto.linkBoleto() : null);
        String pix = safe(boleto != null && boleto.pix() != null ? boleto.pix().copiaCola() : null);

        String origem = safe(meta != null ? meta.origemSistema() : null);
        String criadoEm = safe(meta != null ? meta.criadoEm().toString() : null);
        String contato = safe(meta != null ? meta.admEmail() : null);

        StringBuilder sb = new StringBuilder();

        sb.append("Olá, ").append(nome.isBlank() ? "Associado(a)" : nome).append(".\n\n")
                .append("Estamos enviando o seu boleto atualziado.\n\n")
                .append("Resumo\n")

                .append("- Nosso número: ").append(nossoNumero).append("\n")
                .append("- Referência: ").append(referencia.isBlank() ? "N/D" : referencia).append("\n")

                .append("- Vencimento: ").append(venc).append("\n")
                .append("- Valor: ").append(valor).append("\n")
                .append("- Situação: ").append(status.isBlank() ? "N/D" : status).append("\n\n");

        if (!linha.isBlank()) {
            sb.append("Linha digitável:\n")
                    .append(linha).append("\n\n");
        }

        if (!link.isBlank()) {
            sb.append("Link do boleto:\n").append(link).append("\n\n");
        }

        if (!pix.isBlank()) {
            sb.append("Caso prefira chave PIX (copia e cola):\n").append(pix).append("\n\n");
        }

        sb.append("Observações\n")
                .append("- Esta é uma mensagem automática, de natureza transacional.\n")
                .append("- Para qualquer dúvida pode responder direto a esse e-mail.\n\n")
                .append("Atenciosamente,\n")
                .append("Equipe ProtBike\n");

        if (!contato.isBlank()) {
            sb.append(contato).append("\n");
        }

        // Rodapé técnico opcional (útil pra suporte, mas discreto)
        sb.append("\n---\n")
                .append("Origem: ").append(origem.isBlank() ? "N/D" : origem).append("\n")
                .append("Processamento: ").append(safe(msg.processamentoId())).append("\n");
        if (!criadoEm.isBlank()) sb.append("Cobrança criada em: ").append(criadoEm).append("\n");

        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        a = safe(a);
        if (!a.isBlank()) return a;
        return safe(b);
    }

    private static boolean looksLikeUnavailableMessage(String linha) {
        String s = safe(linha).toLowerCase(LOCALE_BR);
        return s.contains("não foi possível") || s.contains("nao foi possivel");
    }

    private static String formatDateBr(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "N/D";
        try {
            return LocalDate.parse(isoDate, ISO).format(BR);
        } catch (Exception e) {
            return isoDate; // fallback: não quebra email se vier diferente
        }
    }

    private static String formatMoneyBr(String value) {
        if (value == null || value.isBlank()) return "N/D";
        try {
            BigDecimal bd = new BigDecimal(value.trim());
            NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_BR);
            return nf.format(bd);
        } catch (Exception e) {
            return value;
        }
    }
}
