package conexao;

import java.util.Locale;

/*
Monta a resposta de um comando AT a partir dos pedaços lidos da porta serial, sem depender de hardware.
- \r e \n contam como fim de linha (o modem manda "\r\n", às vezes só "\r"); linhas vazias são descartadas.
- A primeira linha igual ao comando enviado é o eco do modem (ATE1) e não entra na resposta.
- A resposta termina na linha OK, ERROR, +CME ERROR: ... ou +CMS ERROR: ... (que fica na resposta,
  pois o AvaliadorResultado decide o veredito por ela).
*/
public class RespostaAt {

    private final String comandoEnviado;
    private final StringBuilder pendente = new StringBuilder();
    private boolean viuConteudo = false;
    private boolean terminou = false;

    public RespostaAt(String comandoEnviado) {
        this.comandoEnviado = comandoEnviado.trim();
    }

    public static boolean ehLinhaFinal(String linha) {
        String l = linha.trim().toUpperCase(Locale.ROOT);
        return l.equals("OK") || l.equals("ERROR")
                || l.startsWith("+CME ERROR") || l.startsWith("+CMS ERROR");
    }

    // Devolve só as linhas completas já limpas; o que vier depois da linha final é ignorado
    public String receber(String pedaco) {
        StringBuilder liberado = new StringBuilder();
        for (int i = 0; i < pedaco.length() && !terminou; i++) {
            char c = pedaco.charAt(i);
            if (c == '\r' || c == '\n') {
                tratarLinha(pendente.toString(), liberado);
                pendente.setLength(0);
            } else {
                pendente.append(c);
            }
        }
        return liberado.toString();
    }

    // Linha final sem quebra (tempo esgotado no meio): entrega o resto como está
    public String finalizar() {
        StringBuilder liberado = new StringBuilder();
        if (!terminou) {
            tratarLinha(pendente.toString(), liberado);
        }
        pendente.setLength(0);
        return liberado.toString();
    }

    public boolean terminou() {
        return terminou;
    }

    private void tratarLinha(String linha, StringBuilder liberado) {
        String limpa = linha.trim();
        if (limpa.isEmpty()) {
            return;
        }
        // O eco só pode ser a primeira linha; depois disso um texto igual ao comando é resposta
        if (!viuConteudo && limpa.equalsIgnoreCase(comandoEnviado)) {
            viuConteudo = true;
            return;
        }
        viuConteudo = true;
        liberado.append(limpa).append('\n');
        if (ehLinhaFinal(limpa)) {
            terminou = true;
        }
    }
}
