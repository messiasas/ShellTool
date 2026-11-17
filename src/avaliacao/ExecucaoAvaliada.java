package avaliacao;

import model.ResultadoExecucao;

import java.time.LocalTime;

// classe responsável por capturar os resultados dos testes
public class ExecucaoAvaliada {

    private final LocalTime hora;
    private final String nomeTeste;
    private final ResultadoExecucao resultado;
    private final ResultadoAvaliado avaliacao;

    public ExecucaoAvaliada(LocalTime hora, String nomeTeste,
                            ResultadoExecucao resultado, ResultadoAvaliado avaliacao) {
        this.hora = hora;
        this.nomeTeste = nomeTeste;
        this.resultado = resultado;
        this.avaliacao = avaliacao;
    }

    public LocalTime getHora() {
        return hora;
    }

    public String getNomeTeste() {
        return nomeTeste;
    }

    public ResultadoExecucao getResultado() {
        return resultado;
    }

    public ResultadoAvaliado getAvaliacao() {
        return avaliacao;
    }
}
