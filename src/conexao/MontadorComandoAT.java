package conexao;

public class MontadorComandoAT {

    public static final String PORTA_AT = "/dev/mux1";

    public static String montarLinha(String comandoAt, int esperaSegundos) {
        return montarLinha(comandoAt, esperaSegundos, PORTA_AT);
    }

    // (sub-shell) evita mensagens de job; cat lê a porta em 2º plano; kill $PID fecha só esse cat
    public static String montarLinha(String comandoAt, int esperaSegundos, String porta) {
        return "(cat " + porta + " & PID=$!; sleep 1; echo -e \""
                + escaparAspas(comandoAt.trim()) + "\\r\" > " + porta
                + "; sleep " + esperaSegundos + "; kill $PID)";
    }

    // Dentro de '...' nada é interpretado: uma aspa simples vira '\'' (fecha, aspa protegida, abre)
    public static String envolverEmSu(String linha) {
        return "su -c '" + linha.replace("'", "'\\''") + "'";
    }

    public static String textoExibicao(String comandoAt) {
        return textoExibicao(comandoAt, PORTA_AT);
    }

    public static String textoExibicao(String comandoAt, String porta) {
        return comandoAt.trim() + " (via " + porta + ")";
    }

    // \ " $ ` recebem uma barra para o shell tratá-los como texto dentro do echo
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
