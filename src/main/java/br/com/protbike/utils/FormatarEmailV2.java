//package br.com.protbike.utils;
//
//import br.com.protbike.records.BoletoNotificacaoMessage;
//import java.math.BigDecimal;
//import java.text.NumberFormat;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.Locale;
//
//public class FormatarEmailV2 {
//
//    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
//    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
//    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
//
//    // Cores padrão (SaaS ready: poderiam vir de um config por tenant)
//    private static final String COLOR_PRIMARY = "#1D4ED8";
//    private static final String COLOR_TEXT_DARK = "#101828";
//    private static final String COLOR_TEXT_MUTED = "#667085";
//    private static final String COLOR_BG_LIGHT = "#F9FAFB";
//    private static final String COLOR_BORDER = "#EAECF0";
//
//    public static String toHtml(BoletoNotificacaoMessage msg) {
//        if (msg == null) return "";
//
//        var boleto = msg.boleto();
//        var meta = msg.meta();
//        var destinatario = msg.destinatario();
//
//        // Formatação prévia
//        String valorStr = formatMoneyBr(boleto.valorBoleto());
//        String vencimentoStr = formatDateBr(boleto.dataVencimento());
//        String nomeCliente = (destinatario.nome() == null || destinatario.nome().isBlank()) ? "Cliente" : destinatario.nome();
//
//        StringBuilder html = new StringBuilder(4000);
//
//        html.append("<!DOCTYPE html>")
//                .append("<html lang=\"pt-BR\">")
//                .append("<head>")
//                .append("<meta charset=\"UTF-8\">")
//                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
//                .append("<title>Fatura ").append(esc(meta.associacaoCliente())).append("</title>")
//                .append("</head>")
//                .append("<body style=\"margin:0;padding:0;background-color:#F6F7F9;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;color:").append(COLOR_TEXT_DARK).append(";\">")
//
//                // Centralizador Principal
//                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color:#F6F7F9;padding:40px 10px;\">")
//                .append("<tr><td align=\"center\">")
//
//                // Card do E-mail
//                .append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color:#FFFFFF;border:1px solid ").append(COLOR_BORDER).append(";border-radius:16px;overflow:hidden;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1);\">")
//
//                // Header
//                .append("<tr><td style=\"padding:32px;border-bottom:1px solid ").append(COLOR_BORDER).append(";\">")
//                .append("<div style=\"font-size:20px;font-weight:700;letter-spacing:-0.5px;\">").append(esc(meta.associacaoCliente())).append("</div>")
//                .append("<div style=\"font-size:14px;color:").append(COLOR_TEXT_MUTED).append(";margin-top:4px;\">Documento de Cobrança</div>")
//                .append("</td></tr>")
//
//                // Body
//                .append("<tr><td style=\"padding:32px;\">")
//                .append("<p style=\"font-size:16px;line-height:24px;margin:0 0 24px 0;\">Olá, <strong>").append(esc(nomeCliente)).append("</strong>,</p>")
//                .append("<p style=\"font-size:15px;line-height:24px;color:#344054;margin:0 0 32px 0;\">")
//                .append("Sua fatura referente a <strong>").append(esc(boleto.mesReferente())).append("</strong> já está disponível para pagamento.")
//                .append("</p>")
//
//                // Bloco de Valor e Vencimento (Destaque visual)
//                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color:").append(COLOR_BG_LIGHT).append(";border-radius:12px;margin-bottom:32px;\">")
//                .append("<tr>")
//                .append("<td style=\"padding:24px;\">")
//                .append("<div style=\"font-size:13px;color:").append(COLOR_TEXT_MUTED).append(";text-transform:uppercase;letter-spacing:1px;margin-bottom:8px;\">Vencimento</div>")
//                .append("<div style=\"font-size:18px;font-weight:700;\">").append(vencimentoStr).append("</div>")
//                .append("</td>")
//                .append("<td style=\"padding:24px;text-align:right;\">")
//                .append("<div style=\"font-size:13px;color:").append(COLOR_TEXT_MUTED).append(";text-transform:uppercase;letter-spacing:1px;margin-bottom:8px;\">Total a pagar</div>")
//                .append("<div style=\"font-size:24px;font-weight:800;color:").append(COLOR_PRIMARY).append(";\">").append(valorStr).append("</div>")
//                .append("</td>")
//                .append("</tr>")
//                .append("</table>")
//
//                // CTA: Botão PDF
//                .append("<div style=\"text-align:center;margin-bottom:32px;\">")
//                .append("<a href=\"").append(escAttr(boleto.linkBoleto())).append("\" style=\"display:block;background-color:").append(COLOR_PRIMARY).append(";color:#FFFFFF;padding:16px 24px;border-radius:8px;text-decoration:none;font-weight:700;font-size:16px;\">")
//                .append("Visualizar Boleto (PDF)</a>")
//                .append("</div>");
//
//        // Pix Area
//        if (boleto.pix() != null && !boleto.pix().copiaCola().isBlank()) {
//            html.append("<div style=\"border:2px dashed ").append(COLOR_BORDER).append(";border-radius:12px;padding:20px;margin-bottom:32px;\">")
//                    .append("<div style=\"font-size:14px;font-weight:700;margin-bottom:12px;display:flex;align-items:center;\">Pagar com PIX</div>")
//                    .append("<div style=\"background:#FFFFFF;padding:12px;border:1px solid ").append(COLOR_BORDER).append(";font-family:monospace;font-size:12px;word-break:break-all;color:#344054;border-radius:6px;\">")
//                    .append(esc(boleto.pix().copiaCola()))
//                    .append("</div>")
//                    .append("<div style=\"font-size:12px;color:").append(COLOR_TEXT_MUTED).append(";margin-top:10px;\">Copie o código acima e cole no aplicativo do seu banco.</div>")
//                    .append("</div>");
//        }
//
//        // Rodapé do Card
//        html.append("<div style=\"font-size:13px;color:").append(COLOR_TEXT_MUTED).append(";line-height:20px;border-top:1px solid ").append(COLOR_BORDER).append(";padding-top:24px;\">")
//                .append("<strong>Dica de Segurança:</strong> Antes de confirmar o pagamento, verifique se o beneficiário final é <strong>").append(esc(meta.associacaoCliente())).append("</strong>.<br><br>")
//                .append("Atenciosamente,<br><strong>Equipe ").append(esc(meta.associacaoApelido())).append("</strong>")
//                .append("</div>")
//                .append("</td></tr>")
//
//                // Footer Externo (Protocolo/Unsubscribe)
//                .append("<tr><td style=\"padding:24px 32px;text-align:center;\">")
//                .append("<div style=\"font-size:11px;color:").append(COLOR_TEXT_MUTED).append(";\">")
//                .append("Protocolo: ").append(esc(msg.numeroProtocolo())).append(" | ID: ").append(esc(msg.processamentoId())).append("<br>")
//                .append("Este é um e-mail transacional enviado por ").append(esc(meta.associacaoCliente()))
//                .append("</div>")
//                .append("</td></tr>")
//
//                .append("</table>")
//                .append("</td></tr>")
//                .append("</table>")
//                .append("</body></html>");
//
//        return html.toString();
//    }
//
//    // --- Helpers de Formatação e Segurança ---
//    private static String formatMoneyBr(String val) {
//        try {
//            return NumberFormat.getCurrencyInstance(LOCALE_BR).format(new BigDecimal(val.trim()));
//        } catch (Exception e) { return "R$ " + val; }
//    }
//
//    private static String formatDateBr(String isoDate) {
//        try {
//            return LocalDate.parse(isoDate, ISO).format(BR);
//        } catch (Exception e) { return isoDate; }
//    }
//
//    private static String esc(String s) {
//        if (s == null) return "";
//        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
//    }
//
//    private static String escAttr(String s) {
//        return esc(s).replaceAll("[\\n\\r]", "");
//    }
//}