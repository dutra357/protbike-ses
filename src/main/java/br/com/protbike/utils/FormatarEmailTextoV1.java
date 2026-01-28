package br.com.protbike.utils;

import br.com.protbike.records.BoletoNotificacaoMessage;

import static br.com.protbike.utils.FormatarEmailV3.*;

public class FormatarEmailTextoV1 {

    public static String toPlainText(BoletoNotificacaoMessage msg, String unsubscribeUrl) {
        if (msg == null) return "";

        // Recupera o separador de linha do sistema (ou fixa CRLF para e-mails)
        final String NL = "\r\n";

        final var destinatario = msg.destinatario();
        final var boleto = msg.boleto();
        final var meta = msg.meta();

        String nome = (destinatario != null) ? safe(destinatario.nome()) : "Associado(a)";
        String protocolo = safe(msg.numeroProtocolo());
        String associacao = (meta != null) ? meta.associacaoCliente() : "PROTBIKE";

        String venc = (boleto != null) ? formatDateBr(boleto.dataVencimento()) : "N/D";
        String valor = (boleto != null) ? formatMoneyBr(boleto.valorBoleto()) : "N/D";
        String linha = (boleto != null) ? firstNonBlank(boleto.linhaDigitavelAtual(), boleto.linhaDigitavel()) : "";
        String pix = (boleto != null && boleto.pix() != null) ? safe(boleto.pix().copiaCola()) : "";
        String link = (boleto != null) ? safe(boleto.linkBoleto()) : "";

        StringBuilder text = new StringBuilder(2000);

        // Header
        text.append(associacao.toUpperCase()).append(NL);
        text.append("E-mail transacional / remessa de boleto").append(NL);
        text.append("------------------------------------------------------------").append(NL).append(NL);

        text.append("Olá, ").append(nome).append(".").append(NL).append(NL);
        text.append("Segue o boleto atualizado para o mês. Sempre confira o recebedor.").append(NL).append(NL);

        // Resumo com String.format e %n para respeitar o alerta
        text.append("RESUMO DO DOCUMENTO:").append(NL);
        text.append("------------------------------------------------------------").append(NL);
        text.append(String.format("%-18s %s%s", "Protocolo:", protocolo, NL));
        text.append(String.format("%-18s %s%s", "Nosso número:", (boleto != null) ? boleto.nossoNumero() : "N/D", NL));
        text.append(String.format("%-18s %s%s", "Competência:", (boleto != null) ? safe(boleto.mesReferente()) : "N/D", NL));
        text.append(String.format("%-18s %s%s", "Vencimento:", venc, NL));
        text.append(String.format("%-18s %s%s", "Valor:", valor, NL));
        text.append("------------------------------------------------------------").append(NL).append(NL);

        if (!link.isEmpty()) {
            text.append("ACESSE SEU BOLETO EM PDF:").append(NL).append(link).append(NL).append(NL);
        }

        if (!linha.isEmpty()) {
            text.append("LINHA DIGITÁVEL:").append(NL).append(linha).append(NL).append(NL);
        }

        if (!pix.isEmpty()) {
            text.append("PIX (COPIA E COLA):").append(NL).append(pix).append(NL).append(NL);
        }

        text.append("------------------------------------------------------------").append(NL);
        text.append("Esta é uma mensagem automática e não deve ser respondida.").append(NL);
        text.append("Dúvidas? Acesse nossos canais de atendimento.").append(NL).append(NL);

        text.append("Atenciosamente,").append(NL);
        text.append("Equipe ").append(meta.associacaoApelido().toUpperCase()).append(NL);
        if (meta.admEmail() != null) text.append(meta.admEmail()).append(NL);

        text.append(NL).append("---").append(NL);
        text.append("Para cancelar o recebimento de boletos eletrônicos, acesse:").append(NL);
        text.append(unsubscribeUrl).append(NL).append(NL);

        text.append(associacao.toUpperCase()).append(NL);
        text.append("CNPJ: 47.788.341/0001-37").append(NL);
        text.append("Rua Antonio Schroeder, 960, sala 2, Bela Vista — São José, SC").append(NL);
        text.append("www.protbike.com.br").append(NL).append(NL);

        text.append("ID de Segurança: ").append(protocolo).append(NL);

        return text.toString();
    }
}