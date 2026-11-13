package model;

/*
 * Resultado de UMA execução de comando no terminal.
 * Todos os campos são final: depois de criado, o resultado não muda (objeto imutável).
 */
public class ResultadoExecucao {

    private final String textoComando;   // comando original, sem o marcador
    private final String saida;          // saída limpa (sem eco, marcador e prompt)
    private final Integer codigoSaida;   // valor de $? ; null se o marcador não chegou
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

    /* Integer (e não int) porque pode ser null: int não aceita "sem valor" */
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
