package avaliacao;

import java.awt.Color;

public enum Veredito {

    OK(new Color(46, 125, 50)),
    ALERTA(new Color(230, 120, 0)),
    FALHA(new Color(198, 40, 40)),
    INFO(new Color(84, 110, 122));

    private final Color cor;

    Veredito(Color cor) {
        this.cor = cor;
    }

    public Color getCor() {
        return cor;
    }
}
