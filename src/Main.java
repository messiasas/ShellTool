import TelaPrincipal.TelaPrincipal;
import model.Comando;
import model.Plataforma;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> new TelaPrincipal().setVisible(true));
    }
}
