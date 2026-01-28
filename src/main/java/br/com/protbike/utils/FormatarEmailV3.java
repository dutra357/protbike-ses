package br.com.protbike.utils;

import br.com.protbike.records.BoletoNotificacaoMessage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FormatarEmailV3 {

    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // NumberFormat não é thread-safe, mas em Lambda a execução é serial por instância.
    // Para segurança total em outros ambientes, use ThreadLocal ou instancie localmente.
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(LOCALE_BR);

    public static String toHtml(BoletoNotificacaoMessage msg, String unsubscribeUrl) {
        if (msg == null) return "";

        final var destinatario = msg.destinatario();
        final var boleto = msg.boleto();
        final var meta = msg.meta();

        // Extração de dados com null-safe
        String nome = (destinatario != null) ? safe(destinatario.nome()) : "";
        String protocolo = safe(msg.numeroProtocolo());
        String nossoNumero = (boleto != null && boleto.nossoNumero() != null) ? String.valueOf(boleto.nossoNumero()) : "N/D";
        String referencia = (boleto != null) ? safe(boleto.mesReferente()) : "";

        // Lógica de Status e Cores
        LocalDate hoje = LocalDate.now();
        LocalDate dataVencimento = null;
        String status;
        String statusBadgeBg;
        String statusBadgeFg;

        try {
            dataVencimento = (boleto != null) ? LocalDate.parse(boleto.dataVencimento(), ISO) : null;
        } catch (Exception ignored) {}

        if (dataVencimento == null) {
            status = "N/D";
            statusBadgeBg = "#F2F4F7";
            statusBadgeFg = "#344054";
        } else if (dataVencimento.isBefore(hoje)) {
            status = "VENCIDO";
            statusBadgeBg = "#FFF1F0"; // Vermelho claro
            statusBadgeFg = "#CF1322"; // Vermelho escuro
        } else {
            status = "ABERTO";
            statusBadgeBg = "#EEF4FF"; // Azul claro
            statusBadgeFg = "#1D4ED8"; // Azul escuro
        }

        String venc = (dataVencimento != null) ? dataVencimento.format(BR) : "N/D";
        String emissao = (boleto != null) ? formatDateBr(boleto.dataEmissao()) : "N/D";
        String valor = (boleto != null) ? formatMoneyBr(boleto.valorBoleto()) : "N/D";

        String linha = (boleto != null) ? firstNonBlank(boleto.linhaDigitavelAtual(), boleto.linhaDigitavel()) : "";
        String link = (boleto != null) ? safe(boleto.linkBoleto()) : "";
        String pix = (boleto != null && boleto.pix() != null) ? safe(boleto.pix().copiaCola()) : "";
        String contato = (meta != null) ? safe(meta.admEmail()) : "";

        StringBuilder html = new StringBuilder(3000);

        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(esc(meta.associacaoCliente())).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#F6F7F9;font-family:Arial,Helvetica,sans-serif;color:#101828;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#F6F7F9;padding:24px 12px;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"width:600px;max-width:600px;background:#FFFFFF;border:1px solid #EAECF0;border-radius:12px;overflow:hidden;\">")

                // Header
                .append("<tr><td style=\"padding:20px 24px;border-bottom:1px solid #EAECF0;\">")
                .append("<div style=\"font-size:18px;font-weight:700;\">").append(esc(meta.associacaoCliente())).append("</div>")
                .append("<div style=\"font-size:13px;color:#667085;margin-top:4px;\">E-mail transacional / remessa de boleto</div></td></tr>")

                // Body Intro
                .append("<tr><td style=\"padding:20px 24px;\">")
                .append("<p style=\"margin:0 0 12px 0;font-size:14px;line-height:20px;\">Olá, ").append(nome.isEmpty() ? "Associado(a)" : esc(nome)).append(".</p>")
                .append("<p style=\"margin:0 0 16px 0;font-size:14px;line-height:20px;color:#344054;\">Segue o boleto atualizado para o mês. <b>Sempre confira o recebedor</b>.</p>")

                // Summary Box
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border:1px solid #EAECF0;border-radius:10px;overflow:hidden;\">")
                .append("<tr><td style=\"padding:14px 14px;background:#FCFCFD;border-bottom:1px solid #EAECF0;\">")
                .append("<span style=\"font-size:14px;font-weight:700;\">Situação</span>")
                .append("<span style=\"display:inline-block;margin-left:10px;padding:4px 8px;border-radius:999px;background:")
                .append(statusBadgeBg).append(";color:").append(statusBadgeFg).append(";font-size:12px;font-weight:700;\">")
                .append(status).append("</span></td></tr>");

        appendRow(html, "Protocolo", protocolo);
        appendRow(html, "Nosso número", nossoNumero);
        appendRow(html, "Competência", referencia.isEmpty() ? "N/D" : referencia);
        appendRow(html, "Data de emissão", emissao);
        appendRow(html, "Vencimento", venc);
        appendRow(html, "Valor", valor);

        html.append("</table>");

        // CTA
        if (!link.isEmpty()) {
            html.append("<div style=\"margin-top:16px;\"><a href=\"").append(escAttr(link))
                    .append("\" style=\"display:inline-block;background:#1D4ED8;color:#FFFFFF;text-decoration:none;padding:10px 14px;border-radius:10px;font-size:14px;font-weight:700;\">Boleto em PDF</a></div>");
        }

        // Linha Digitável
        if (!linha.isEmpty()) {
            html.append("<div style=\"margin-top:18px;\"><div style=\"font-size:13px;font-weight:700;margin-bottom:6px;\">Linha digitável</div>")
                    .append("<div style=\"font-size:13px;line-height:18px;color:#344054;background:#F9FAFB;border:1px solid #EAECF0;padding:10px 12px;border-radius:10px;word-break:break-word;\">")
                    .append(esc(linha)).append("</div></div>");
        }

        // PIX
        if (!pix.isEmpty()) {
            html.append("<div style=\"margin-top:18px;\"><div style=\"font-size:13px;font-weight:700;margin-bottom:6px;\">PIX (copia e cola)</div>")
                    .append("<div style=\"font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;line-height:16px;color:#344054;background:#F9FAFB;border:1px solid #EAECF0;padding:10px 12px;border-radius:10px;word-break:break-all;\">")
                    .append(esc(pix)).append("</div></div>");
        }

        // Footer e Assinatura
        html.append("<div style=\"margin-top:20px;padding-top:16px;border-top:1px solid #EAECF0;text-align:center;font-family:Arial, sans-serif;\">")
                .append("<p style=\"margin:0 0 4px 0;font-size:12px;line-height:18px;color:#667085;\">Esta é uma mensagem automática e não deve ser respondida.</p>")
                .append("<p style=\"margin:0 0 16px 0;font-size:12px;line-height:18px;color:#667085;\">Dúvidas? Acesse nossos canais de atendimento.</p>")

                // Assinatura
                .append("<div style=\"margin-bottom:20px;font-size:14px;line-height:20px;color:#101828;\">")
                .append("<div style=\"font-weight:700;\">Atenciosamente,</div>")
                .append("<div style=\"margin-top:2px;\">Equipe ").append(esc(meta.associacaoApelido().toUpperCase())).append("</div>");

        if (!contato.isEmpty()) {
            html.append("<div style=\"margin-top:6px;font-size:13px;\">")
                    .append("<a href=\"mailto:").append(escAttr(contato))
                    .append("\" style=\"color:#1D4ED8;text-decoration:none;font-weight:500;\">").append(esc(contato)).append("</a></div>");
        }
        html.append("</div>") // Fecha bloco de assinatura

                // Bloco de Descadastramento (CAN-SPAM Compliance)
                .append("<div style=\"margin:20px 0;padding:12px;background-color:#F9FAFB;border-radius:8px;\">")
                .append("<p style=\"margin:0;font-size:11px;line-height:16px;color:#667085;\">")
                .append("Respeitamos sua privacidade. Se não deseja mais receber estes boletos por e-mail, ")
                .append("<a href=\"").append(escAttr(unsubscribeUrl))
                .append("\" style=\"color:#1D4ED8;text-decoration:underline;font-weight:500;\">clique aqui para cancelar o envio eletrônico</a>.")
                .append("</p></div>")

                // Informações Corporativas e Legais
                .append("<address style=\"font-style:normal;font-size:11px;line-height:18px;color:#98A2B3;\">")
                .append("<span style=\"font-weight:700;color:#667085;\">").append(esc(meta.associacaoCliente().toUpperCase())).append("</span><br>")
                .append("CNPJ: 47.788.341/0001-37<br>")
                .append("Rua Antonio Schroeder, 960, sala 2, Bela Vista — São José, SC — CEP 88.110-401<br>")

                // Links Institucionais
                .append("<div style=\"margin-top:8px;\">")
                .append("<a href=\"https://www.protbike.com.br\" style=\"color:#98A2B3;text-decoration:none;\">www.protbike.com.br</a>")
                .append("  •  ")
                .append("<a href=\"https://www.protbike.com.br/privacidade\" style=\"color:#98A2B3;text-decoration:none;\">Política de Privacidade</a>")
                .append("</div>")
                .append("</address>")
                .append("</div>"); // Fecha o container principal do footer

// Rodapé Técnico (Protocolo)
        html.append("</td></tr>")
                .append("<tr><td style=\"padding:16px 24px;background:#FCFCFD;border-top:1px solid #EAECF0;text-align:center;\">")
                .append("<div style=\"font-size:10px;color:#98A2B3;line-height:14px;text-transform:uppercase;letter-spacing:0.8px;\">")
                .append("ID de Segurança: ").append(esc(protocolo))
                .append("  |  Ref: ").append(esc(safe(msg.meta().csvProcessamentoId())))
                .append("</div></td></tr>")
                .append("</table></td></tr></table></body></html>");

        return html.toString();
    }

    private static void appendRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><td style=\"padding:10px 14px;border-top:1px solid #EAECF0;font-size:13px;color:#667085;width:40%;\">")
                .append(esc(label))
                .append("</td><td style=\"padding:10px 14px;border-top:1px solid #EAECF0;font-size:13px;color:#101828;font-weight:700;\">")
                .append(esc(value == null || value.isEmpty() ? "N/D" : value))
                .append("</td></tr>");
    }

    protected static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    protected static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        return (b != null) ? b.trim() : "";
    }

    protected static String formatDateBr(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "N/D";
        try {
            return LocalDate.parse(isoDate, ISO).format(BR);
        } catch (Exception e) { return isoDate; }
    }

    protected static String formatMoneyBr(String value) {
        if (value == null || value.isBlank()) return "N/D";
        try {
            return CURRENCY_FORMAT.format(new BigDecimal(value.trim()));
        } catch (Exception e) { return value; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 10);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escAttr(String s) {
        return esc(s).replace("\n", "").replace("\r", "");
    }
}