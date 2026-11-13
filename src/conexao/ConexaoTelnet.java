package conexao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class ConexaoTelnet {

    // Bytes de controle do protocolo Telnet
    private static final int IAC  = 255;
    private static final int DONT = 254;
    private static final int WILL = 251;
    private static final int SB   = 250;
    private static final int SE   = 240;

    private static final int ESC = 27;

    private Socket socket;
    private InputStream entrada;
    private OutputStream saida;

    public void conectar(String host, int porta,
                         Consumer<String> aoReceber,
                         Runnable aoDesconectar) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, porta), 5000);
        entrada = socket.getInputStream();
        saida = socket.getOutputStream();

        Thread leitor = new Thread(() -> lerContinuamente(aoReceber, aoDesconectar));
        leitor.setDaemon(true);
        leitor.start();
    }

    public void enviar(String comando) throws IOException {
        saida.write((comando + "\r\n").getBytes(StandardCharsets.UTF_8));
        saida.flush();
    }

    // Envia bytes "crus", sem acrescentar \r\n (usado no Ctrl+C = byte 0x03)
    public void enviarBytes(byte[] bytes) throws IOException {
        saida.write(bytes);
        saida.flush();
    }

    private void lerContinuamente(Consumer<String> aoReceber, Runnable aoDesconectar) {
        byte[] buffer = new byte[1024];
        int estado = 0; // 0 = texto normal, 1 = após IAC, 2 = esperando opção,
        // 3 = dentro de SB, 4 = IAC dentro de SB
        try {
            int lidos;
            while ((lidos = entrada.read(buffer)) != -1) {
                ByteArrayOutputStream texto = new ByteArrayOutputStream();

                for (int i = 0; i < lidos; i++) {
                    int b = buffer[i] & 0xFF;

                    switch (estado) {
                        case 0:
                            if (b == IAC) {
                                estado = 1;
                            } else if (b == ESC) {
                                estado = 5;
                            } else if (b != '\r') {
                                texto.write(b);
                            }
                            break;
                        case 1:
                            if (b == IAC) {
                                texto.write(b);
                                estado = 0;
                            } else if (b >= WILL && b <= DONT) {
                                estado = 2;
                            } else if (b == SB) {
                                estado = 3;
                            } else {
                                estado = 0;
                            }
                            break;
                        case 2:
                            estado = 0;
                            break;
                        case 3:
                            if (b == IAC) {
                                estado = 4;
                            }
                            break;
                        case 4:
                            estado = (b == SE) ? 0 : 3;
                            break;
                        case 5: // logo após ESC
                            estado = (b == '[') ? 6 : 0;
                            break;
                        case 6: // dentro de ESC[ ... até a letra final
                            if (b >= 0x40 && b <= 0x7E) {
                                estado = 0;
                            }
                            break;
                    }
                }

                if (texto.size() > 0) {
                    aoReceber.accept(texto.toString(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            // o socket foi fechado
        }
        aoDesconectar.run();
    }

    public void desconectar() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // ao fechar, um erro não importa
        }
        socket = null;
    }

    public boolean estaConectado() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}