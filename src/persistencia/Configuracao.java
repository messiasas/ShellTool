package persistencia;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//lê o arquivo config.txt (formato chave=valor) e oferece os valores para o resto do programa

/*o congif.txt serve para a ferramenta saber qual comando usar, se for diferente, o usuario podera configurar manualmente*/
public class Configuracao {

    private static final String PADRAO_ADB = "adb";
    private static final String PADRAO_PORTA_AT = "";
    private static final String PADRAO_SU = "no";
    private static final String PADRAO_CMD_SN = "getprop ro.serialno";
    private static final String PADRAO_CMD_PN = "getprop ro.fac.pn";
    private static final String PADRAO_MODO_AT_ANDROID = "serial";
    private static final String PADRAO_PORTA_SERIAL_AT = "";
    private static final String PADRAO_BAUD_SERIAL_AT = "115200";

    private final Path arquivo;

    //mapa das chaves para entender o chave=valor
    private final Map<String, String> valores = new HashMap<>();

    //arquivo guarda o caminho completo, por exemplo
    public Configuracao() {
        arquivo = PastaDados.obter().resolve("config.txt");
    }

    public Path getArquivo() {
        return arquivo;
    }

    public void carregar() throws IOException {
        valores.clear();

        // nao existe?
        if (!Files.exists(arquivo)) {
            criarPadrao();
            return;
        }

        //Abrir o arquivo
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

    //a geração do arquivo inicial
    private void criarPadrao() throws IOException {
        Files.createDirectories(arquivo.getParent());
        String texto = String.join("\n",
                "# ShellTool settings (key=value format; lines starting with # are comments)",
                "",
                "# Path to adb. \"adb\" uses the system PATH.",
                "# Example: adb=C:\\platform-tools\\adb.exe",
                "adb=" + PADRAO_ADB,
                "",
                "# How AT commands reach the Android modem:",
                "#   serial = through the PC serial (COM) port the terminal exposes over USB (no root needed)",
                "#            UNISOC (A910S, A950, A960): \"SPRD LTE AT\" port; Qualcomm (A930): \"Qualcomm HS-USB Modem\"",
                "#   adb    = writes to the modem device on the terminal via adb shell (uses porta_at_android/su_android)",
                "modo_at_android=" + PADRAO_MODO_AT_ANDROID,
                "",
                "# PC serial port for AT on Android (e.g. COM12). Empty = detect automatically.",
                "# The port chosen on the main screen takes priority over this value.",
                "porta_serial_at=" + PADRAO_PORTA_SERIAL_AT,
                "",
                "# Serial port speed (8N1, RTS and DTR on)",
                "baud_serial_at=" + PADRAO_BAUD_SERIAL_AT,
                "",
                "# Only used when modo_at_android=adb:",
                "# Modem serial port on the terminal for AT commands (e.g. /dev/smd11).",
                "# Varies by manufacturer and requires root. Empty = AT via adb disabled.",
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

    //O valor() devolve o que o usuário escreveu no config.txt para aquela chave ou,
    // se ela estiver vazia ou não existir, devolve o valor padrão, sem alterar nada.
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


    /*O isSuAndroid() lê a opção su_android do config.txt e devolve true se ela for “yes” ou “sim”
    (e false em qualquer outro caso), para o programa saber se deve rodar os comandos AT no Android com su -c, como root.*/
    public boolean isSuAndroid() {
        String su = valor("su_android", PADRAO_SU);
        return su.equalsIgnoreCase("yes") || su.equalsIgnoreCase("sim");
    }

    // Só "adb" volta ao caminho antigo; ausente, vazio ou outro valor = serial (config.txt antigos não têm a chave)
    public boolean isAtAndroidViaSerial() {
        return !valor("modo_at_android", PADRAO_MODO_AT_ANDROID).equalsIgnoreCase("adb");
    }

    public String getPortaSerialAt() {
        return valor("porta_serial_at", PADRAO_PORTA_SERIAL_AT);
    }

    // Valor inválido não deve impedir o uso: cai no padrão
    public int getBaudSerialAt() {
        try {
            int baud = Integer.parseInt(valor("baud_serial_at", PADRAO_BAUD_SERIAL_AT));
            return baud > 0 ? baud : Integer.parseInt(PADRAO_BAUD_SERIAL_AT);
        } catch (NumberFormatException e) {
            return Integer.parseInt(PADRAO_BAUD_SERIAL_AT);
        }
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
