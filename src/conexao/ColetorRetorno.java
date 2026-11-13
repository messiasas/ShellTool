package conexao;

import model.ResultadoExecucao;
import model.StatusExecucao;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Junta os pedaços de texto que chegam do terminal durante UMA execução
 * e descobre quando ela terminou, procurando a linha __FIM_<n>__:<código>.
 *
 * Não usa nada do Swing: só recebe texto e devolve texto/resultado.
 * Quem chama (a tela) deve usar o coletor sempre na mesma thread (a EDT),
 * por isso não é preciso sincronização aqui.
 */
public class ColetorRetorno {

    // Como o eco do comando aparece: com as aspas "" no meio, ver montarLinhaComMarcador()
    private static final String TRECHO_ECO = "__F\"\"IM_";

    // Qualquer marcador (de qualquer número), usado para descartar marcadores atrasados de execuções antigas
    private static final Pattern QUALQUER_MARCADOR = Pattern.compile("^.*__FIM_\\d+__:\\d+\\s*$");

    // Linha que é só o prompt do shell, ex.: "~ $", "~ #", "/tmp #"
    private static final Pattern PROMPT = Pattern.compile("^\\S*\\s*[$#]\\s*$");

    /*
     * Os dados do socket chegam em pedaços de tamanho qualquer. Uma linha
     * (inclusive o marcador) pode chegar partida: "__FI" numa leitura e "M_3__:0\n" na outra.
     * Por isso o texto é acumulado aqui e só linhas COMPLETAS (terminadas em \n) são analisadas.
     * StringBuilder é usado porque concatenar String várias vezes cria um objeto novo a cada "+".
     */
    private final StringBuilder pendente = new StringBuilder();

    // Saída limpa da execução atual
    private final StringBuilder saidaLimpa = new StringBuilder();

    private boolean emAndamento = false;
    private int numeroAtual;
    private String textoComando;
    private long inicioNanos;
    private Pattern marcadorAtual;

    // Texto que chegou DEPOIS do marcador (normalmente o prompt), para a tela exibir no fim
    private String sobra = "";

    /*
     * Monta a linha que realmente vai para o terminal:
     *   <comando>; echo "__F""IM_<n>__:$?"
     *
     * O truque das aspas: o shell ECOA tudo o que digitamos. Se o marcador fosse escrito
     * direto (__FIM_3__), o próprio eco do comando já conteria __FIM_3__ e pareceria o fim.
     * Com "__F""IM_3__:$?", o eco mostra literalmente __F""IM_3__ (não casa com o marcador),
     * mas o shell junta as strings "__F" e "IM_3__:$?" ao executar o echo, e a saída real
     * fica __FIM_3__:0  (com $? trocado pelo código de saída do comando).
     */
    public static String montarLinhaComMarcador(String comando, int numero) {
        // tira ";" e espaços do final para não gerar ";;" (erro de sintaxe no shell)
        String limpo = comando.replaceAll("[;\\s]+$", "");
        // "cmd &" seguido de ";" também é erro de sintaxe; nesse caso basta um espaço
        String separador = limpo.endsWith("&") ? " " : "; ";
        return limpo + separador + "echo \"__F\"\"IM_" + numero + "__:$?\"";
    }

    /* Começa a coletar uma nova execução */
    public void iniciar(int numero, String textoComando) {
        this.numeroAtual = numero;
        this.textoComando = textoComando;
        this.inicioNanos = System.nanoTime();
        // o marcador só vale para o número desta execução; (.*?) pega texto que veio antes
        // na mesma linha (ex.: saída sem \n no final, como "printf abc")
        this.marcadorAtual = Pattern.compile("^(.*?)__FIM_" + numero + "__:(\\d{1,9})\\s*$");
        pendente.setLength(0);
        saidaLimpa.setLength(0);
        sobra = "";
        emAndamento = true;
    }

    public boolean emAndamento() {
        return emAndamento;
    }

    public int getNumeroAtual() {
        return numeroAtual;
    }

    /*
     * Recebe mais um pedaço de texto do terminal.
     * - exibir: recebe o texto que pode ser mostrado ao vivo (sem eco e sem marcador).
     * - Devolve o ResultadoExecucao quando o marcador chega; senão, devolve null.
     */
    public ResultadoExecucao receber(String pedaco, Consumer<String> exibir) {
        if (!emAndamento) {
            return null; // fora de execução: o coletor ignora (a tela mostra direto)
        }

        pendente.append(pedaco);
        StringBuilder paraExibir = new StringBuilder();

        int fimLinha;
        // processa cada linha completa; o resto (linha ainda sem \n) fica em "pendente"
        while ((fimLinha = pendente.indexOf("\n")) >= 0) {
            String linha = pendente.substring(0, fimLinha);
            pendente.delete(0, fimLinha + 1);

            // 1) eco do comando digitado: não mostra e não guarda
            if (linha.contains(TRECHO_ECO)) {
                continue;
            }

            // 2) marcador desta execução: fim!
            Matcher m = marcadorAtual.matcher(linha);
            if (m.matches()) {
                String antes = m.group(1);
                if (!antes.isEmpty()) {
                    adicionarNaSaida(antes);
                    paraExibir.append(antes).append('\n');
                }
                enviarSeHouver(paraExibir, exibir);

                // o que veio depois do marcador (ex.: o prompt "~ $ ") é guardado como sobra
                sobra = pendente.toString();
                pendente.setLength(0);
                return terminar(StatusExecucao.CONCLUIDO, Integer.valueOf(m.group(2)));
            }

            // 3) marcador atrasado de uma execução anterior (ex.: que estourou o tempo): descarta
            if (QUALQUER_MARCADOR.matcher(linha).matches()) {
                continue;
            }

            // 4) linha normal: mostra ao vivo e guarda na saída limpa (exceto se for só o prompt)
            paraExibir.append(linha).append('\n');
            adicionarNaSaida(linha);
        }

        enviarSeHouver(paraExibir, exibir);
        return null;
    }

    /*
     * Encerra a execução sem o marcador (tempo esgotado, cancelado, conexão perdida).
     * O pedaço de linha que ainda não tinha \n é exibido e entra na saída.
     * Devolve null se não havia execução em andamento.
     */
    public ResultadoExecucao finalizar(StatusExecucao status, Consumer<String> exibir) {
        if (!emAndamento) {
            return null;
        }
        String resto = pendente.toString();
        pendente.setLength(0);
        if (!resto.isEmpty() && !resto.contains(TRECHO_ECO)) {
            exibir.accept(resto);
            adicionarNaSaida(resto);
        }
        return terminar(status, null);
    }

    /* Devolve (e esvazia) o texto que chegou depois do marcador */
    public String retirarSobra() {
        String s = sobra;
        sobra = "";
        return s;
    }

    private ResultadoExecucao terminar(StatusExecucao status, Integer codigo) {
        emAndamento = false;
        long duracaoMs = (System.nanoTime() - inicioNanos) / 1_000_000;
        return new ResultadoExecucao(textoComando, saidaLimpa.toString(), codigo, duracaoMs, status);
    }

    private void adicionarNaSaida(String linha) {
        if (!PROMPT.matcher(linha).matches()) {
            saidaLimpa.append(linha).append('\n');
        }
    }

    private void enviarSeHouver(StringBuilder texto, Consumer<String> exibir) {
        if (texto.length() > 0) {
            exibir.accept(texto.toString());
        }
    }
}
