import TelaPrincipal.TelaPrincipal;
import model.Comando;
import model.Plataforma;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {

       /* Comando teste = new Comando(
                "Ping ipv6 Google",
                Plataforma.ANDROID,
                "ping6 -c 3 2001:4860:4860::8888",
                "Testa conectividade IPv6 sem DNS");

        System.out.println(teste);
        System.out.println(teste.getPlataforma() +" -> "+teste.getTxtComando());*/

        SwingUtilities.invokeLater(() -> new TelaPrincipal().setVisible(true));
    }
}
