package persistencia;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Configuracao {

    private static final String PADRAO_ADB = "adb";
    private static final String PADRAO_PORTA_AT = "";
    private static final String PADRAO_SU = "nao";
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
                "# Configurações do ShellTool (formato chave=valor; linhas com # são comentários)",
                "",
                "# Caminho do adb. \"adb\" usa o PATH do sistema.",
                "# Exemplo: adb=C:\\platform-tools\\adb.exe",
                "adb=" + PADRAO_ADB,
                "",
                "# Porta serial do modem para comandos AT no Android (ex.: /dev/smd11).",
                "# Varia por fabricante e exige root. Vazio = AT no Android desativado.",
                "porta_at_android=" + PADRAO_PORTA_AT,
                "",
                "# sim = executa a linha do AT via su -c (necessário para acessar a porta do modem)",
                "su_android=" + PADRAO_SU,
                "",
                "# Comandos usados pelo \"Registrar teste\" no Android para ler SN e PN",
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
        return valor("su_android", PADRAO_SU).equalsIgnoreCase("sim");
    }

    public String getCmdSnAndroid() {
        return valor("cmd_sn_android", PADRAO_CMD_SN);
    }

    public String getCmdPnAndroid() {
        return valor("cmd_pn_android", PADRAO_CMD_PN);
    }
}
