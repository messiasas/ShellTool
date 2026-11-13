package avaliacao;

import java.awt.Color;

/*
 * Classificação final de uma execução. Cada valor guarda a cor usada na tabela.
 * As cores são escuras o bastante para o texto branco ficar legível por cima.
 */
public enum Veredito {

    OK(new Color(46, 125, 50)),       // verde
    ALERTA(new Color(230, 120, 0)),   // laranja
    FALHA(new Color(198, 40, 40)),    // vermelho
    INFO(new Color(84, 110, 122));    // cinza-azulado

    private final Color cor;

    Veredito(Color cor) {
        this.cor = cor;
    }

    public Color getCor() {
        return cor;
    }
}
