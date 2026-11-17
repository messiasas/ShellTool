package persistencia;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuracao {

    private static final String PADRAO_ADB = "adb";
    private static final String PADRAO_PORTA_AT = "";
    private static final String PADRAO_SU = "no";
    private static final String PADRAO_CMD_SN = "getprop ro.serialno";
    private static final String PADRAO_CMD_PN = "getprop ro.fac.pn";

    private final Path arquivo;
    private final Map<String, String> valores = new HashMap<>();

    public Configuracao() {
        arquivo = PastaDados.obter().resolve("config.txt");
    }

    public Path getArquivo() {
        return arquivo;
    }

    public void carregar() throws IOException {
        valores.clear();
        if (!Files.exists(arquivo)) {
            criarPadrao();
            return;
        }

        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String linha;
            boolean primeiraLinha = true;
            while ((linha = leitor.readLine()) != null) {
                if (primeiraLinha && linha.startsWith("\uFEFF")) {
                    linha = linha.substring(1);
                }
                primeiraLinha = false;

                String limpa = linha.trim();
                if (limpa.isEmpty() || limpa.startsWith("#")) {
                    continue;
                }
                // Separa no primeiro "=": o valor pode conter outros
                int posicao = limpa.indexOf('=');
                if (posicao < 0) {
                    continue;
                }
                valores.put(limpa.substring(0, posicao).trim(), limpa.substring(posicao + 1).trim());
            }
        }
    }

    private void criarPadrao() throws IOException {
        Files.createDirectories(arquivo.getParent());
        String texto = String.join("\n",
                "# ShellTool settings (key=value format; lines starting with # are comments)",
                "",
                "# Path to adb. \"adb\" uses the system PATH.",
                "# Example: adb=C:\\platform-tools\\adb.exe",
                "adb=" + PADRAO_ADB,
                "",
                "# Modem serial port for AT commands on Android (e.g. /dev/smd11).",
                "# Varies by manufacturer and requires root. Empty = AT on Android disabled.",
                "porta_at_android=" + PADRAO_PORTA_AT,
                "",
                "# yes = runs the AT line via su -c (needed to access the modem port)",
                "su_android=" + PADRAO_SU,
                "",
                "# Commands used by \"Record test\" on Android to read SN and PN",
                "cmd_sn_android=" + PADRAO_CMD_SN,
                "cmd_pn_android=" + PADRAO_CMD_PN,
                "");
        Files.writeString(arquivo, texto.replace("\n", "\r\n"), StandardCharsets.UTF_8);
    }

    private String valor(String chave, String padrao) {
        String v = valores.get(chave);
        return (v == null || v.isEmpty()) ? padrao : v;
    }

    public String getAdb() {
        return valor("adb", PADRAO_ADB);
    }

    public String getPortaAtAndroid() {
        return valor("porta_at_android", PADRAO_PORTA_AT);
    }

    public boolean isSuAndroid() {
        String su = valor("su_android", PADRAO_SU);
        return su.equalsIgnoreCase("yes") || su.equalsIgnoreCase("sim");
    }

    public String getCmdSnAndroid() {
        return valor("cmd_sn_android", PADRAO_CMD_SN);
    }

    public String getCmdPnAndroid() {
        return valor("cmd_pn_android", PADRAO_CMD_PN);
    }

    public void setAdb(String caminho) throws IOException {
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        boolean trocou = false;
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).replace("\uFEFF", "").trim().startsWith("adb=")) {
                linhas.set(i, "adb=" + caminho);
                trocou = true;
                break;
            }
        }
        if (!trocou) {
            linhas.add("adb=" + caminho);
        }
        Files.write(arquivo, linhas, StandardCharsets.UTF_8);
        valores.put("adb", caminho);
    }
}
