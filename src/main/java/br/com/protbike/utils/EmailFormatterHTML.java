package br.com.protbike.utils;

import br.com.protbike.records.BoletoNotificacaoMessage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EmailFormatterHTML {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    EmailFormatterHTML() {}

    public static String toHtml(BoletoNotificacaoMessage msg) {

        var destinatario = msg.destinatario();
        var boleto = msg.boleto();
        var meta = msg.meta();

        String nome = safe(destinatario != null ? destinatario.nome() : null);
        String email = safe(destinatario != null ? destinatario.email() : null);

        String protocolo = safe(msg.numeroProtocolo());
        String nossoNumero = boleto != null && boleto.nossoNumero() != null ? String.valueOf(boleto.nossoNumero()) : "N/D";
        String referencia = safe(boleto != null ? boleto.mesReferente() : null);
        String status = safe(boleto != null ? boleto.descricaoSituacaoBoleto() : null);

        String venc = formatDateBr(boleto != null ? boleto.dataVencimento() : null);
        String emissao = formatDateBr(boleto != null ? boleto.dataEmissao() : null);
        String valor = formatMoneyBr(boleto != null ? boleto.valorBoleto() : null);

        String linha = firstNonBlank(
                boleto != null ? boleto.linhaDigitavelAtual() : null,
                boleto != null ? boleto.linhaDigitavel() : null
        );

        String link = safe(boleto != null ? boleto.linkBoleto() : null);
        String pix = safe(boleto != null && boleto.pix() != null ? boleto.pix().copiaCola() : null);

        String contato = safe(meta != null ? meta.admEmail() : null);

        // UI: cor do status (bem simples)
        String statusBadgeBg = "BAIXADO".equalsIgnoreCase(status) ? "#F2F4F7" : "#EEF4FF";
        String statusBadgeFg = "BAIXADO".equalsIgnoreCase(status) ? "#344054" : "#1D4ED8";

        StringBuilder html = new StringBuilder(2500);

        html.append("<!doctype html>")
                .append("<html lang=\"pt-BR\">")
                .append("<head>")
                .append("<meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(esc(msg.numeroProtocolo())).append("</title>")
                .append("</head>")
                .append("<body style=\"margin:0;padding:0;background:#F6F7F9;font-family:Arial,Helvetica,sans-serif;color:#101828;\">")

                // container
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#F6F7F9;padding:24px 12px;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"width:600px;max-width:600px;background:#FFFFFF;border:1px solid #EAECF0;border-radius:12px;overflow:hidden;\">")

                // header
                .append("<tr><td style=\"padding:20px 24px;border-bottom:1px solid #EAECF0;\">")
                .append("<div style=\"font-size:18px;font-weight:700;\">ProtBike</div>")
                .append("<div style=\"font-size:13px;color:#667085;margin-top:4px;\">Mensagem transacional — atualização de boleto</div>")
                .append("</td></tr>")

                // body intro
                .append("<tr><td style=\"padding:20px 24px;\">")
                .append("<p style=\"margin:0 0 12px 0;font-size:14px;line-height:20px;\">")
                .append("Olá, ").append(esc(nome.isBlank() ? "cliente" : nome)).append(".")
                .append("</p>")
                .append("<p style=\"margin:0 0 16px 0;font-size:14px;line-height:20px;color:#344054;\">")
                .append("Segue uma atualização sobre o seu boleto. Guarde o protocolo para suporte.")
                .append("</p>")

                // summary box
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border:1px solid #EAECF0;border-radius:10px;overflow:hidden;\">")
                .append("<tr><td style=\"padding:14px 14px;background:#FCFCFD;border-bottom:1px solid #EAECF0;\">")
                .append("<span style=\"font-size:14px;font-weight:700;\">Resumo</span>")
                .append("<span style=\"display:inline-block;margin-left:10px;padding:4px 8px;border-radius:999px;background:")
                .append(statusBadgeBg).append(";color:").append(statusBadgeFg).append(";font-size:12px;font-weight:700;\">")
                .append(esc(status.isBlank() ? "N/D" : status))
                .append("</span>")
                .append("</td></tr>")

                .append(row("Protocolo", protocolo))
                .append(row("Nosso número", nossoNumero))
                .append(row("Referência", referencia.isBlank() ? "N/D" : referencia))
                .append(row("Emissão", emissao))
                .append(row("Vencimento", venc))
                .append(row("Valor", valor))
                .append("</table>")

        // boleto link CTA
        ;

        if (!link.isBlank()) {
            html.append("<div style=\"margin-top:16px;\">")
                    .append("<a href=\"").append(escAttr(link)).append("\" ")
                    .append("style=\"display:inline-block;background:#1D4ED8;color:#FFFFFF;text-decoration:none;")
                    .append("padding:10px 14px;border-radius:10px;font-size:14px;font-weight:700;\">")
                    .append("Acessar boleto</a>")
                    .append("</div>");
        }

        // linha digitável
        if (!linha.isBlank()) {
            html.append("<div style=\"margin-top:18px;\">")
                    .append("<div style=\"font-size:13px;font-weight:700;margin-bottom:6px;\">Linha digitável</div>")
                    .append("<div style=\"font-size:13px;line-height:18px;color:#344054;background:#F9FAFB;border:1px solid #EAECF0;")
                    .append("padding:10px 12px;border-radius:10px;word-break:break-word;\">")
                    .append(esc(linha))
                    .append("</div>")
                    .append("</div>");
        }

        // PIX copia e cola
        if (!pix.isBlank()) {
            html.append("<div style=\"margin-top:18px;\">")
                    .append("<div style=\"font-size:13px;font-weight:700;margin-bottom:6px;\">PIX (copia e cola)</div>")
                    .append("<div style=\"font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;line-height:16px;")
                    .append("color:#344054;background:#F9FAFB;border:1px solid #EAECF0;padding:10px 12px;border-radius:10px;")
                    .append("word-break:break-all;\">")
                    .append(esc(pix))
                    .append("</div>")
                    .append("</div>");
        }

        // observações
        html.append("<div style=\"margin-top:20px;padding-top:16px;border-top:1px solid #EAECF0;\">")
                .append("<p style=\"margin:0 0 8px 0;font-size:12px;line-height:18px;color:#667085;\">")
                .append("Esta é uma mensagem automática, de natureza transacional.")
                .append("</p>")
                .append("<p style=\"margin:0;font-size:12px;line-height:18px;color:#667085;\">")
                .append("Em caso de divergência, responda este e-mail informando o protocolo <strong>")
                .append(esc(protocolo)).append("</strong>.")
                .append("</p>")
                .append("</div>")

                // assinatura
                .append("<div style=\"margin-top:16px;font-size:14px;line-height:20px;\">")
                .append("<div style=\"font-weight:700;\">Atenciosamente,</div>")
                .append("<div>Equipe ProtBike</div>");

        if (!contato.isBlank()) {
            html.append("<div style=\"margin-top:6px;font-size:13px;color:#344054;\">")
                    .append("<a href=\"mailto:").append(escAttr(contato)).append("\" style=\"color:#1D4ED8;text-decoration:none;\">")
                    .append(esc(contato)).append("</a>")
                    .append("</div>");
        } else if (!email.isBlank()) {
            // só pra não ficar vazio; opcional
            html.append("<div style=\"margin-top:6px;font-size:12px;color:#667085;\">")
                    .append("Destinatário: ").append(esc(email))
                    .append("</div>");
        }

        html.append("</div>") // end signature
                .append("</td></tr>")

                // footer
                .append("<tr><td style=\"padding:14px 24px;background:#FCFCFD;border-top:1px solid #EAECF0;\">")
                .append("<div style=\"font-size:11px;color:#667085;line-height:16px;\">")
                .append("Protocolo: ").append(esc(protocolo))
                .append(" • Processamento: ").append(esc(safe(msg.processamentoId())))
                .append("</div>")
                .append("</td></tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        return html.toString();
    }

    // ---------- helpers ----------

    private static String row(String label, String value) {
        return "<tr>" +
                "<td style=\"padding:10px 14px;border-top:1px solid #EAECF0;font-size:13px;color:#667085;width:40%;\">" +
                esc(label) +
                "</td>" +
                "<td style=\"padding:10px 14px;border-top:1px solid #EAECF0;font-size:13px;color:#101828;font-weight:700;\">" +
                esc(value == null || value.isBlank() ? "N/D" : value) +
                "</td>" +
                "</tr>";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        a = safe(a);
        if (!a.isBlank()) return a;
        return safe(b);
    }

    private static String formatDateBr(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "N/D";
        try {
            return LocalDate.parse(isoDate, ISO).format(BR);
        } catch (Exception e) {
            return isoDate;
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

    // Escape básico pra conteúdo HTML (texto)
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Escape pra atributo HTML (href/mailto etc.)
    private static String escAttr(String s) {
        return esc(s).replace("\n", "").replace("\r", "");
    }
}

