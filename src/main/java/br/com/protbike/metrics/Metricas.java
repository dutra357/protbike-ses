package br.com.protbike.metrics;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class Metricas {

    public int mensagensSqsTotal;
    public int boletosTotal;

    public int enviosSucesso;
    public int enviosFalha;

    public int boletosSucesso;
    public int boletosFalha;


    @Override
    public String toString() {
        return String.format(
                "boletosTotal=%d, boletosSucesso=%d, boletosFalha=%d, enviosSucesso=%d, enviosFalha=%d",
                boletosTotal, boletosSucesso, boletosFalha, enviosSucesso, enviosFalha
        );
    }
}