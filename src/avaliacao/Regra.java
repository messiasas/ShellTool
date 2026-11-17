package avaliacao;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Regra {

    private static final String PREFIXO_REGEX = "re:";
    private static final String SEPARADOR_ROTULO = " => ";
    private static final int TAMANHO_MAX_DETALHE = 80;

    private final Veredito tipo;
    private final String padrao;
    private final String rotulo;
    private final boolean regex;
    private final boolean ausencia;

    private Pattern padraoCompilado;
    private boolean regexInvalida = false;

    public Regra(Veredito tipo, String padrao, String rotulo, boolean regex) {
        this(tipo, padrao, rotulo, regex, false);
    }

    public Regra(Veredito tipo, String padrao, String rotulo, boolean regex, boolean ausencia) {
        this.tipo = tipo;
        this.padrao = padrao;
        this.rotulo = (rotulo == null) ? "" : rotulo.trim();
        this.regex = regex;
        this.ausencia = ausencia;
    }

    public static Regra deTextoAusencia(String valor) {
        Regra base = deTexto(Veredito.FALHA, valor);
        return new Regra(Veredito.FALHA, base.padrao, base.rotulo, base.regex, true);
    }

    public static Regra deTexto(Veredito tipo, String valor) {
        String padrao = valor;
        String rotulo = "";

        int posicao = valor.lastIndexOf(SEPARADOR_ROTULO);
        if (posicao >= 0) {
            padrao = valor.substring(0, posicao);
            rotulo = valor.substring(posicao + SEPARADOR_ROTULO.length());
        }

        boolean ehRegex = padrao.startsWith(PREFIXO_REGEX);
        if (ehRegex) {
            padrao = padrao.substring(PREFIXO_REGEX.length());
        }
        return new Regra(tipo, padrao, rotulo, ehRegex);
    }

    public String getChave() {
        return ausencia ? "falha_sem" : tipo.name().toLowerCase(java.util.Locale.ROOT);
    }

    public String paraTexto() {
        String texto = (regex ? PREFIXO_REGEX : "") + padrao;
        if (!rotulo.isEmpty()) {
            texto += SEPARADOR_ROTULO + rotulo;
        }
        return texto;
    }

    public String verificar(String saida) {
        int posicao = -1;

        if (regex) {
            Pattern p = compilar();
            if (p == null) {
                return null;
            }
            Matcher m = p.matcher(saida);
            if (m.find()) {
                posicao = m.start();
            }
        } else {
            posicao = saida.indexOf(padrao);
        }
        if (ausencia) {
            if (posicao >= 0 || (regex && compilar() == null)) {
                return null;
            }
            return rotulo.isEmpty() ? "Not found: " + padrao : rotulo;
        }

        if (posicao < 0) {
            return null;
        }
        return rotulo.isEmpty() ? linhaNaPosicao(saida, posicao) : rotulo;
    }

    private Pattern compilar() {
        if (padraoCompilado == null && !regexInvalida) {
            try {
                padraoCompilado = Pattern.compile(padrao, Pattern.MULTILINE);
            } catch (PatternSyntaxException e) {
                regexInvalida = true;
                System.err.println("Rule ignored, invalid regular expression: " + padrao
                        + " (" + e.getDescription() + ")");
            }
        }
        return padraoCompilado;
    }

    private static String linhaNaPosicao(String texto, int posicao) {
        int inicio = texto.lastIndexOf('\n', posicao - 1) + 1;
        int fim = texto.indexOf('\n', posicao);
        if (fim < 0) {
            fim = texto.length();
        }
        String linha = texto.substring(inicio, fim).trim();
        if (linha.length() > TAMANHO_MAX_DETALHE) {
            linha = linha.substring(0, TAMANHO_MAX_DETALHE) + "...";
        }
        return linha;
    }

    public Veredito getTipo() {
        return tipo;
    }

    public String getPadrao() {
        return padrao;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean isAusencia() {
        return ausencia;
    }

    @Override
    public String toString() {
        return (ausencia ? "FAIL_IF_MISSING" : tipo.toString()) + ": " + paraTexto();
    }
}
