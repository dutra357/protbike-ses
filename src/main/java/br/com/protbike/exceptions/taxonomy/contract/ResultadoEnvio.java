package br.com.protbike.exceptions.taxonomy.contract;

public interface ResultadoEnvio {
    boolean sucesso();
    boolean retryavel();
    String protocolo();
    String motivo();
}