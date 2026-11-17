package avaliacao;

import java.awt.Color;

// name() fica em português porque é gravado no testes.txt (ok=, alerta=, falha=); toString() é o texto da tela
public enum Veredito {

    OK("OK", new Color(46, 125, 50)),
    ALERTA("WARNING", new Color(230, 120, 0)),
    FALHA("FAIL", new Color(198, 40, 40)),
    INFO("INFO", new Color(84, 110, 122));

    private final String rotulo;
    private final Color cor;

    Veredito(String rotulo, Color cor) {
        this.rotulo = rotulo;
        this.cor = cor;
    }

    public Color getCor() {
        return cor;
    }

    @Override
    public String toString() {
        return rotulo;
    }
}
