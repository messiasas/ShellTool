package avaliacao;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/*
 * Uma regra diz: "se a saída contiver <padrão>, o veredito é <tipo>".
 * O padrão pode ser um texto simples (contém) ou uma expressão regular.
 *
 * No testes.txt ela fica assim:   falha=re:\b100% packet loss => Sem resposta
 *   - "re:" no começo  -> expressão regular
 *   - " => " separa o padrão do rótulo (opcional)
 *
 * Regra de AUSÊNCIA (chave falha_sem): o contrário, gera FALHA quando o padrão NÃO aparece.
 *   falha_sem=re:(?m)^default => Sem rota
 */
public class Regra {

    private static final String PREFIXO_REGEX = "re:";
    private static final String SEPARADOR_ROTULO = " => ";
    private static final int TAMANHO_MAX_DETALHE = 80;

    private final Veredito tipo;      // OK, ALERTA ou FALHA
    private final String padrao;
    private final String rotulo;      // pode ser vazio
    private final boolean regex;
    private final boolean ausencia;   // true = falha_sem: casa quando o padrão NÃO é encontrado

    // A expressão regular é compilada só uma vez e reaproveitada
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

    /* Regra falha_sem: o tipo é sempre FALHA, mesma sintaxe das outras */
    public static Regra deTextoAusencia(String valor) {
        Regra base = deTexto(Veredito.FALHA, valor);
        return new Regra(Veredito.FALHA, base.padrao, base.rotulo, base.regex, true);
    }

    /*
     * Cria a regra a partir do valor escrito no arquivo, ex.: "re:(?m)^default => Rota OK".
     * Usa o ÚLTIMO " => " para separar, assim o padrão pode conter "=>" se precisar.
     */
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

    /* Chave usada no arquivo: ok, alerta, falha ou falha_sem */
    public String getChave() {
        return ausencia ? "falha_sem" : tipo.name().toLowerCase(java.util.Locale.ROOT);
    }

    /* Caminho inverso do deTexto: o valor que vai depois do "ok=", "alerta=" ou "falha=" */
    public String paraTexto() {
        String texto = (regex ? PREFIXO_REGEX : "") + padrao;
        if (!rotulo.isEmpty()) {
            texto += SEPARADOR_ROTULO + rotulo;
        }
        return texto;
    }

    /*
     * Procura o padrão na saída.
     * Devolve o detalhe (rótulo, ou a linha que casou) se casar; null se não casar.
     * Na regra de ausência é o contrário: "casa" quando o padrão NÃO foi encontrado.
     */
    public String verificar(String saida) {
        int posicao = -1;

        if (regex) {
            Pattern p = compilar();
            if (p == null) {
                return null; // regex inválida: a regra é ignorada
            }
            Matcher m = p.matcher(saida);
            if (m.find()) {
                posicao = m.start();
            }
        } else {
            posicao = saida.indexOf(padrao); // "contém", diferenciando maiúsculas/minúsculas
        }

        if (ausencia) {
            if (posicao >= 0 || (regex && compilar() == null)) {
                return null; // encontrou (ou regex inválida): a regra não se aplica
            }
            return rotulo.isEmpty() ? "Não encontrado: " + padrao : rotulo;
        }

        if (posicao < 0) {
            return null;
        }
        return rotulo.isEmpty() ? linhaNaPosicao(saida, posicao) : rotulo;
    }

    /*
     * Compila a regex uma vez. Se for inválida, registra no console e
     * devolve null, sem derrubar a aplicação.
     */
    private Pattern compilar() {
        if (padraoCompilado == null && !regexInvalida) {
            try {
                // MULTILINE: ^ e $ valem para cada linha, não só para o texto inteiro
                padraoCompilado = Pattern.compile(padrao, Pattern.MULTILINE);
            } catch (PatternSyntaxException e) {
                regexInvalida = true;
                System.err.println("Regra ignorada, expressão regular inválida: " + padrao
                        + " (" + e.getDescription() + ")");
            }
        }
        return padraoCompilado;
    }

    /* Recorta a linha inteira onde está a posição encontrada, limitada a ~80 caracteres */
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
        return (ausencia ? "FALHA_SEM" : tipo.name()) + ": " + paraTexto();
    }
}
