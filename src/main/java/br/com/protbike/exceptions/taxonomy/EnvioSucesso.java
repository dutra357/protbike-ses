package br.com.protbike.exceptions.taxonomy;

import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;

public class EnvioSucesso implements ResultadoEnvio {

    private final String protocolo;

    public EnvioSucesso(String protocolo) {
        this.protocolo = protocolo;
    }

    public boolean sucesso() { return true; }

    public boolean retryavel() { return false; }

    public String protocolo() { return protocolo; }

    public String motivo() { return null; }

}