package br.com.protbike.context;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class TenantContext {

    private String tenantId;
    private String nomeAssociacao;
    // Outros dados que você carrega uma vez (configurações do tenant, features flags)

    public void init(String tenantId, String nome) {
        this.tenantId = tenantId;
        this.nomeAssociacao = nome;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getNomeAssociacao() {
        return nomeAssociacao;
    }
}