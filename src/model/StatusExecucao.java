package model;

public enum StatusExecucao {
    CONCLUIDO("Completed"),
    TEMPO_ESGOTADO("Timed out"),
    CANCELADO("Cancelled"),
    CONEXAO_PERDIDA("Connection lost");

    private final String rotulo;

    StatusExecucao(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String toString() {
        return rotulo;
    }
}
