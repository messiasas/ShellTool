package avaliacao;

/* O que o avaliador concluiu: o veredito e um detalhe curto explicando o porquê */
public class ResultadoAvaliado {

    private final Veredito veredito;
    private final String detalhe;

    public ResultadoAvaliado(Veredito veredito, String detalhe) {
        this.veredito = veredito;
        this.detalhe = detalhe;
    }

    public Veredito getVeredito() {
        return veredito;
    }

    public String getDetalhe() {
        return detalhe;
    }

    @Override
    public String toString() {
        return veredito + " - " + detalhe;
    }
}
