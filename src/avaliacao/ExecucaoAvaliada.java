package avaliacao;

import model.ResultadoExecucao;

import java.time.LocalTime;

/*
 * Uma linha da tabela de resultados (e do registro):
 * quando terminou, o nome exibido, o resultado bruto e a avaliação.
 */
public class ExecucaoAvaliada {

    private final LocalTime hora;
    private final String nomeTeste;          // nome do teste, "Livre: ...", "PN" ou "SN"
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
