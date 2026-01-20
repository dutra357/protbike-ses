package br.com.protbike.exceptions.taxonomy;

import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;

public class EnvioFalhaNaoRetryavel implements ResultadoEnvio {

    private final String protocolo;
    private final String motivo;

    public EnvioFalhaNaoRetryavel(String protocolo, String motivo) {
        this.protocolo = protocolo;
        this.motivo = motivo;
    }

    public boolean sucesso() { return false; }

    public boolean retryavel() { return false; }

    public String protocolo() { return protocolo; }

    public String motivo() { return motivo; }
}
