package br.com.protbike.metrics;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class Metricas {

    public int mensagensSqsTotal;
    public int boletosTotal;

    public int enviosSucesso;
    public int enviosFalha;

    public void reset() {
        mensagensSqsTotal = 0;
        boletosTotal = 0;
        enviosSucesso = 0;
        enviosFalha = 0;
    }

    @Override
    public String toString() {
        return String.format(
                "boletosTotal=%d, enviosSucesso=%d, enviosFalha=%d",
                boletosTotal, enviosSucesso, enviosFalha
        );
    }
}