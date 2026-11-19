package conexao;

import com.fazecast.jSerialComm.SerialPort;
import model.StatusExecucao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/*
Envia comandos AT ao modem do terminal Android pela porta serial (COM) que ele expõe ao PC via USB:
UNISOC (A910S, A950, A960) -> "SPRD LTE AT"; Qualcomm (A930) -> "Qualcomm HS-USB Modem 9091".
A porta é aberta e fechada a cada comando, para não prendê-la (ex.: SSCOM) entre um teste e outro.
*/
public class ConexaoSerial {

    public static final int BAUD_PADRAO = 115200;

    // Códigos do Windows devolvidos por getLastErrorCode() quando openPort() falha
    private static final int ERRO_ARQUIVO_NAO_ENCONTRADO = 2;
    private static final int ERRO_ACESSO_NEGADO = 5;

    private static final int LEITURA_TIMEOUT_MS = 200;
    private static final int ESCRITA_TIMEOUT_MS = 2000;

    private volatile SerialPort portaAberta;
    private volatile boolean cancelado;

    public static class Porta {
        public final String nome;
        public final String descricao;
        // Texto que o USB informa (BusReportedDeviceDesc); às vezes difere do nome no Gerenciador de Dispositivos
        public final String descricaoUsb;

        public Porta(String nome, String descricao, String descricaoUsb) {
            this.nome = nome;
            this.descricao = descricao;
            this.descricaoUsb = descricaoUsb;
        }

        @Override
        public String toString() {
            return descricao.isEmpty() ? nome : nome + " - " + descricao;
        }
    }

    public static List<Porta> listarPortas() throws IOException {
        SerialPort[] portas;
        try {
            portas = SerialPort.getCommPorts();
        } catch (LinkageError e) {
            // A DLL nativa é extraída para uma pasta temporária; antivírus ou permissão podem impedir
            throw new IOException("Could not load the serial port library (jSerialComm): " + e.getMessage(), e);
        }
        List<Porta> lista = new ArrayList<>();
        for (SerialPort p : portas) {
            String nome = p.getSystemPortName();
            lista.add(new Porta(nome, semNomeDaPorta(p.getDescriptivePortName(), nome),
                    textoOuVazio(p.getPortDescription())));
        }
        return lista;
    }

    public static Porta detectarPortaAt() throws IOException {
        return detectarPortaAt(listarPortas());
    }

    public static Porta detectarPortaAt(List<Porta> portas) {
        for (Porta p : portas) {
            if (ehPortaAt(p.descricao + " " + p.descricaoUsb)) {
                return p;
            }
        }
        return null;
    }

    // DIAG/LOG/WCN também são portas do modem, mas não aceitam AT (ou aceitam para outro chip, no caso da WCN)
    public static boolean ehPortaAt(String descricao) {
        String d = descricao.toUpperCase(Locale.ROOT);
        if (d.contains("DIAG") || d.contains("LOG") || d.contains("WCN")) {
            return false;
        }
        return d.contains("SPRD LTE AT") || d.contains("HS-USB MODEM");
    }

    /*
    Abre a porta aqui mesmo, para que porta ocupada/inexistente vire IOException na hora (como o adb ao iniciar);
    a leitura roda numa thread daemon e termina com aoTerminar:
    CONCLUIDO (veio a linha final), TEMPO_ESGOTADO, CONEXAO_PERDIDA (erro de leitura) ou CANCELADO.
    */
    public void executar(String porta, int baud, String comandoAt, int tempoMaximoSegundos,
                         Consumer<String> aoReceber, Consumer<StatusExecucao> aoTerminar) throws IOException {
        SerialPort sp;
        try {
            sp = SerialPort.getCommPort(porta);
        } catch (LinkageError e) {
            throw new IOException("Could not load the serial port library (jSerialComm): " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IOException("Invalid serial port \"" + porta + "\"", e);
        }

        sp.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        sp.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
                LEITURA_TIMEOUT_MS, ESCRITA_TIMEOUT_MS);

        if (!sp.openPort()) {
            throw new IOException(mensagemFalhaAbertura(porta, sp.getLastErrorCode()));
        }

        // A PAX pede RTS e DTR ligados; sem DTR alguns modems ignoram o que chega
        sp.setRTS();
        sp.setDTR();
        // Descarta resto de uma resposta anterior ou mensagem espontânea do modem
        sp.flushIOBuffers();

        byte[] dados = (comandoAt.trim() + "\r").getBytes(StandardCharsets.ISO_8859_1);
        if (sp.writeBytes(dados, dados.length) != dados.length) {
            int codigo = sp.getLastErrorCode();
            sp.closePort();
            throw new IOException("Could not write to " + porta + " (error " + codigo + ")");
        }

        cancelado = false;
        portaAberta = sp;
        Thread leitor = new Thread(() -> lerResposta(sp, comandoAt, tempoMaximoSegundos, aoReceber, aoTerminar));
        leitor.setDaemon(true);
        leitor.start();
    }

    // Fechar a porta destrava o readBytes da thread de leitura
    public void cancelar() {
        cancelado = true;
        SerialPort sp = portaAberta;
        if (sp != null) {
            sp.closePort();
        }
    }

    private void lerResposta(SerialPort sp, String comandoAt, int tempoMaximoSegundos,
                             Consumer<String> aoReceber, Consumer<StatusExecucao> aoTerminar) {
        RespostaAt resposta = new RespostaAt(comandoAt);
        long limite = System.nanoTime() + tempoMaximoSegundos * 1_000_000_000L;
        byte[] buffer = new byte[1024];
        StatusExecucao status = StatusExecucao.TEMPO_ESGOTADO;

        try {
            while (System.nanoTime() < limite) {
                if (cancelado) {
                    status = StatusExecucao.CANCELADO;
                    break;
                }
                int lidos = sp.readBytes(buffer, buffer.length);
                if (lidos < 0) {
                    // Cancelar fecha a porta e o readBytes falha: não é perda de conexão
                    if (cancelado) {
                        status = StatusExecucao.CANCELADO;
                    } else {
                        status = StatusExecucao.CONEXAO_PERDIDA;
                        enviarSeHouver("[Serial port read error (error " + sp.getLastErrorCode()
                                + "): terminal disconnected?]\n", aoReceber);
                    }
                    break;
                }
                // ISO-8859-1: um byte = um caractere, então um pedaço nunca corta um caractere ao meio
                enviarSeHouver(resposta.receber(new String(buffer, 0, lidos, StandardCharsets.ISO_8859_1)), aoReceber);
                if (resposta.terminou()) {
                    status = StatusExecucao.CONCLUIDO;
                    break;
                }
            }
        } finally {
            sp.closePort();
            if (portaAberta == sp) {
                portaAberta = null;
            }
        }
        enviarSeHouver(resposta.finalizar(), aoReceber);
        aoTerminar.accept(status);
    }

    private static String mensagemFalhaAbertura(String porta, int codigo) {
        switch (codigo) {
            case ERRO_ACESSO_NEGADO:
                return porta + " is busy: another program (SSCOM, PuTTY, QPST...) has it open. "
                        + "Close it and try again.";
            case ERRO_ARQUIVO_NAO_ENCONTRADO:
                return porta + " was not found: the terminal may be disconnected or the port number changed. "
                        + "Click Refresh.";
            default:
                return "Could not open " + porta + " (error " + codigo + ")";
        }
    }

    // O nome amigável do Windows vem como "SPRD LTE AT (COM12)": tira o "(COM12)" repetido
    private static String semNomeDaPorta(String descricao, String nome) {
        String d = textoOuVazio(descricao);
        String sufixo = "(" + nome + ")";
        if (d.endsWith(sufixo)) {
            d = d.substring(0, d.length() - sufixo.length()).trim();
        }
        return d.equals(nome) ? "" : d;
    }

    private static String textoOuVazio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static void enviarSeHouver(String texto, Consumer<String> aoReceber) {
        if (!texto.isEmpty()) {
            aoReceber.accept(texto);
        }
    }
}
