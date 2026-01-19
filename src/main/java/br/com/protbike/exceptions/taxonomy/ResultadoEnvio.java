package br.com.protbike.exceptions.taxonomy;

public interface ResultadoEnvio {
    boolean sucesso();
    boolean retryavel();
    String protocolo();
    String motivo();
}