package br.com.protbike.utils;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.BoletoNotificacaoMessage.*;
import br.com.protbike.records.enuns.CanalEntrega;

import java.time.Instant;
import java.util.Set;

public class BoletoNotificacaoMessageBuilder {

    private String numeroProtocolo = "PROT-123";
    private String tipoEvento = "DISPONIBILIDADE_BOLETO";
    private Set<CanalEntrega> canais = Set.of(CanalEntrega.EMAIL);

    // Sub-records com valores padrão para facilitar o "Happy Path"
    private Destinatario destinatario = new Destinatario(
            "Fulano de Tal", "12345678901", "5511999999999", "fulano@teste.com");

    private Pix pix = new Pix("pix-copia-e-cola-mock");
    private VeiculoResumo veiculo = new VeiculoResumo("ABC-1234", "Honda CB 500");

    private Boleto boleto = new Boleto(
            123456, "Janeiro/2026", "2026-02-10", "2026-01-20",
            "34191.00000 00000.000000 00000.000000 1 00000000000000",
            "34191.00000 00000.000000 00000.000000 1 00000000000000",
            "150.00", "EM ABERTO", "https://link-boleto.com",
            pix, veiculo);

    private Meta meta = new Meta(
            "tenant-mock", Instant.now(), "SISTEMA_ORIGEM", "arquivo.csv",
            "adm@protbike.com.br", "proc-id-123", "Associação Teste", "AssocApelido");

    public static BoletoNotificacaoMessageBuilder novo() {
        return new BoletoNotificacaoMessageBuilder();
    }

    public BoletoNotificacaoMessageBuilder comProtocolo(String protocolo) {
        this.numeroProtocolo = protocolo;
        return this;
    }

    public BoletoNotificacaoMessageBuilder comCanais(CanalEntrega... canais) {
        this.canais = Set.of(canais);
        return this;
    }

    public BoletoNotificacaoMessageBuilder comDestinatario(String nome, String email) {
        this.destinatario = new Destinatario(nome, "12345678901", "5511999999999", email);
        return this;
    }

    public BoletoNotificacaoMessageBuilder comMeta(String tenantId, String processamentoId) {
        this.meta = new Meta(
                tenantId, Instant.now(), "SISTEMA_ORIGEM", "arquivo.csv",
                "adm@protbike.com.br", processamentoId, "Associação Teste", "AssocApelido");
        return this;
    }

    // Permite sobrescrever sub-objetos inteiros se necessário
    public BoletoNotificacaoMessageBuilder customDestinatario(Destinatario d) {
        this.destinatario = d;
        return this;
    }

    public BoletoNotificacaoMessage build() {
        return new BoletoNotificacaoMessage(
                numeroProtocolo, tipoEvento, canais, destinatario, boleto, meta);
    }
}