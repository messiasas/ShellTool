package model;

public class ResultadoExecucao {

    private final String textoComando;
    private final String saida;
    private final Integer codigoSaida;
    private final long duracaoMs;
    private final StatusExecucao status;

    public ResultadoExecucao(String textoComando, String saida, Integer codigoSaida,
                             long duracaoMs, StatusExecucao status) {
        this.textoComando = textoComando;
        this.saida = saida;
        this.codigoSaida = codigoSaida;
        this.duracaoMs = duracaoMs;
        this.status = status;
    }

    public String getTextoComando() {
        return textoComando;
    }

    public String getSaida() {
        return saida;
    }

    public Integer getCodigoSaida() {
        return codigoSaida;
    }

    public long getDuracaoMs() {
        return duracaoMs;
    }

    public StatusExecucao getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return status + " | código " + codigoSaida + " | " + duracaoMs + " ms | " + textoComando;
    }
}
