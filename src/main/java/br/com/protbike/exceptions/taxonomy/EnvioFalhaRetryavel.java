package br.com.protbike.exceptions.taxonomy;

public class EnvioFalhaRetryavel implements ResultadoEnvio {

    private final String protocolo;
    private final String motivo;

    public EnvioFalhaRetryavel(String protocolo, String motivo) {
        this.protocolo = protocolo;
        this.motivo = motivo;
    }

    public boolean sucesso() { return false; }

    public boolean retryavel() { return true; }

    public String protocolo() { return protocolo; }

    public String motivo() { return motivo; }
}