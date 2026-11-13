package conexao;

/*
 * Transforma um comando AT (ex.: AT+CSQ) na linha de shell que conversa com o modem.
 * Não usa nada do Swing: só recebe texto e devolve texto.
 */
public class MontadorComandoAT {

    /*
     * Porta serial (virtual) do modem no Prolin. Para usar outra porta,
     * altere SOMENTE este valor: a montagem e a exibição usam esta constante.
     */
    public static final String PORTA_AT = "/dev/mux1";

    /*
     * Monta:  (cat /dev/mux1 & PID=$!; sleep 1; echo -e "AT+CSQ\r" > /dev/mux1; sleep 2; kill $PID)
     *
     * - ( ... )       sub-shell: o "&" roda dentro dele, então o ash não imprime mensagens
     *                 de job como "[1] 1234" ou "[1]+ Done" na nossa saída.
     * - cat ... &     abre a porta e fica lendo em segundo plano; tudo que o modem
     *                 responder aparece na saída.
     * - PID=$!        $! é o PID do último processo em segundo plano: o cat que acabamos de abrir.
     * - sleep 1       dá tempo de o cat abrir a porta antes de escrevermos nela.
     * - echo -e "..\r" o -e faz o \r virar o caractere CR, que é o "Enter" que o modem espera.
     * - sleep <espera> tempo para o modem responder.
     * - kill $PID     fecha SÓ esse cat. "killall cat" mataria qualquer cat do terminal,
     *                 inclusive de outros processos.
     *
     * O marcador de fim (ColetorRetorno.montarLinhaComMarcador) é aplicado depois, normalmente.
     */
    public static String montarLinha(String comandoAt, int esperaSegundos) {
        return "(cat " + PORTA_AT + " & PID=$!; sleep 1; echo -e \""
                + escaparAspas(comandoAt.trim()) + "\\r\" > " + PORTA_AT
                + "; sleep " + esperaSegundos + "; kill $PID)";
    }

    /* Texto mostrado na área de retorno no lugar da linha montada */
    public static String textoExibicao(String comandoAt) {
        return comandoAt.trim() + " (via " + PORTA_AT + ")";
    }

    /*
     * O comando vai entre aspas duplas no echo. Comandos como AT+CGDCONT=1,"IP","apn"
     * têm aspas, então \ " $ ` recebem uma barra antes para o shell tratá-los como texto.
     */
    private static String escaparAspas(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if (c == '\\' || c == '"' || c == '$' || c == '`') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
