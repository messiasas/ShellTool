package conexao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConexaoAdb {

    private static final String MARCA_RC = "__RC__:";
    private static final Pattern LINHA_RC = Pattern.compile("^(.*?)__RC__:(-?\\d{1,9})\\s*$");

    private final String adb;

    private volatile Process processo;

    public ConexaoAdb(String adb) {
        this.adb = adb;
    }

    public static class Dispositivos {
        public final List<String> prontos = new ArrayList<>();
        public final List<String> naoAutorizados = new ArrayList<>();
        public final List<String> offline = new ArrayList<>();
    }

    public Dispositivos listarDispositivos() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(adb, "devices");
        pb.redirectErrorStream(true);
        Process p = pb.start();

        Dispositivos d = new Dispositivos();
        try (BufferedReader leitor = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                String[] partes = linha.trim().split("\\s+");
                if (partes.length != 2) {
                    continue;
                }
                switch (partes[1]) {
                    case "device":       d.prontos.add(partes[0]); break;
                    case "unauthorized": d.naoAutorizados.add(partes[0]); break;
                    case "offline":      d.offline.add(partes[0]); break;
                    default:             break;
                }
            }
        }
        try {
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return d;
    }

    public void executar(String serial, String comando,
                         Consumer<String> aoReceber, Consumer<Integer> aoTerminar) throws IOException {
        String linha = montarLinha(comando);

        // A linha vai como UM argumento: quem interpreta ; | $? e aspas é o shell do Android
        ProcessBuilder pb = new ProcessBuilder(adb, "-s", serial, "shell", argumentoDoProcesso(linha));
        pb.redirectErrorStream(true);

        Process p = pb.start();
        processo = p;

        Thread leitor = new Thread(() -> lerAteOFim(p, aoReceber, aoTerminar));
        leitor.setDaemon(true);
        leitor.start();
    }

    public void cancelar() {
        Process p = processo;
        if (p != null) {
            p.destroyForcibly();
        }
    }

    // Sem o truque das aspas do Prolin: no adb não existe eco do comando
    private static String montarLinha(String comando) {
        String limpo = comando.replaceAll("[;\\s]+$", "");
        String separador = limpo.endsWith("&") ? " " : "; ";
        return limpo + separador + "echo \"" + MARCA_RC + "$?\"";
    }

    // No Windows o Java não protege as aspas internas do argumento: cada " vira \" (regra do Windows)
    private static String argumentoDoProcesso(String arg) {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        boolean javaJaProtege = "false".equalsIgnoreCase(
                System.getProperty("jdk.lang.Process.allowAmbiguousCommands"));
        if (!windows || javaJaProtege) {
            return arg;
        }
        StringBuilder sb = new StringBuilder();
        int barras = 0;
        for (char c : arg.toCharArray()) {
            if (c == '\\') {
                barras++;
                continue;
            }
            if (c == '"') {
                sb.append("\\".repeat(barras * 2 + 1));
            } else {
                sb.append("\\".repeat(barras));
            }
            barras = 0;
            sb.append(c);
        }
        sb.append("\\".repeat(barras));
        return sb.toString();
    }

    // Fim da saída = fim do processo; o código vem do __RC__ ou, se ele faltar, do exitValue
    private void lerAteOFim(Process p, Consumer<String> aoReceber, Consumer<Integer> aoTerminar) {
        FiltroRc filtro = new FiltroRc();
        try (Reader leitor = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int lidos;
            while ((lidos = leitor.read(buffer)) != -1) {
                enviarSeHouver(filtro.filtrar(new String(buffer, 0, lidos)), aoReceber);
            }
        } catch (IOException e) {
        }
        enviarSeHouver(filtro.finalizar(), aoReceber);

        Integer codigo = filtro.codigo;
        try {
            p.waitFor();
            if (codigo == null) {
                codigo = p.exitValue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (processo == p) {
            processo = null;
        }
        aoTerminar.accept(codigo);
    }

    private static void enviarSeHouver(String texto, Consumer<String> aoReceber) {
        if (!texto.isEmpty()) {
            aoReceber.accept(texto);
        }
    }

    // Tira a linha __RC__ da saída; segura o trecho final que ainda pode virar o marcador
    private static class FiltroRc {
        private final StringBuilder pendente = new StringBuilder();
        Integer codigo;

        String filtrar(String pedaco) {
            pendente.append(pedaco.replace("\r", ""));
            StringBuilder liberado = new StringBuilder();

            int fimLinha;
            while ((fimLinha = pendente.indexOf("\n")) >= 0) {
                String linha = pendente.substring(0, fimLinha);
                pendente.delete(0, fimLinha + 1);
                if (!tratarMarcador(linha, liberado)) {
                    liberado.append(linha).append('\n');
                }
            }

            int segurar = inicioPossivelMarcador();
            liberado.append(pendente, 0, segurar);
            pendente.delete(0, segurar);
            return liberado.toString();
        }

        String finalizar() {
            String resto = pendente.toString();
            pendente.setLength(0);
            StringBuilder liberado = new StringBuilder();
            if (!resto.isEmpty() && !tratarMarcador(resto, liberado)) {
                liberado.append(resto);
            }
            return liberado.toString();
        }

        private boolean tratarMarcador(String linha, StringBuilder liberado) {
            Matcher m = LINHA_RC.matcher(linha);
            if (!m.matches()) {
                return false;
            }
            codigo = Integer.valueOf(m.group(2));
            if (!m.group(1).isEmpty()) {
                liberado.append(m.group(1)).append('\n');
            }
            return true;
        }

        private int inicioPossivelMarcador() {
            int achou = pendente.indexOf(MARCA_RC);
            if (achou >= 0) {
                return achou;
            }
            int maximo = Math.min(pendente.length(), MARCA_RC.length() - 1);
            for (int k = maximo; k > 0; k--) {
                if (MARCA_RC.startsWith(pendente.substring(pendente.length() - k))) {
                    return pendente.length() - k;
                }
            }
            return pendente.length();
        }
    }
}
