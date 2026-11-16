package conexao;

import model.ResultadoExecucao;
import model.StatusExecucao;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColetorRetorno {

    // Como o eco do comando aparece: com as aspas "" no meio (ver montarLinhaComMarcador)
    private static final String TRECHO_ECO = "__F\"\"IM_";

    private static final Pattern QUALQUER_MARCADOR = Pattern.compile("^.*__FIM_\\d+__:\\d+\\s*$");

    private static final Pattern PROMPT = Pattern.compile("^\\S*\\s*[$#]\\s*$");

    // O texto chega em pedaços: só linhas completas (terminadas em \n) são analisadas
    private final StringBuilder pendente = new StringBuilder();

    private final StringBuilder saidaLimpa = new StringBuilder();

    private boolean emAndamento = false;
    private int numeroAtual;
    private String textoComando;
    private long inicioNanos;
    private Pattern marcadorAtual;

    private String sobra = "";

    // Truque das aspas: o eco mostra __F""IM_ (não casa), mas o echo imprime __FIM_<n>__:<código>
    public static String montarLinhaComMarcador(String comando, int numero) {
        String limpo = comando.replaceAll("[;\\s]+$", "");
        String separador = limpo.endsWith("&") ? " " : "; ";
        return limpo + separador + "echo \"__F\"\"IM_" + numero + "__:$?\"";
    }

    public void iniciar(int numero, String textoComando) {
        this.numeroAtual = numero;
        this.textoComando = textoComando;
        this.inicioNanos = System.nanoTime();
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

    public ResultadoExecucao receber(String pedaco, Consumer<String> exibir) {
        if (!emAndamento) {
            return null;
        }

        pendente.append(pedaco);
        StringBuilder paraExibir = new StringBuilder();

        int fimLinha;
        while ((fimLinha = pendente.indexOf("\n")) >= 0) {
            String linha = pendente.substring(0, fimLinha);
            pendente.delete(0, fimLinha + 1);

            if (linha.contains(TRECHO_ECO)) {
                continue;
            }

            Matcher m = marcadorAtual.matcher(linha);
            if (m.matches()) {
                String antes = m.group(1);
                if (!antes.isEmpty()) {
                    adicionarNaSaida(antes);
                    paraExibir.append(antes).append('\n');
                }
                enviarSeHouver(paraExibir, exibir);

                sobra = pendente.toString();
                pendente.setLength(0);
                return terminar(StatusExecucao.CONCLUIDO, Integer.valueOf(m.group(2)));
            }

            if (QUALQUER_MARCADOR.matcher(linha).matches()) {
                continue;
            }

            paraExibir.append(linha).append('\n');
            adicionarNaSaida(linha);
        }

        enviarSeHouver(paraExibir, exibir);
        return null;
    }

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
