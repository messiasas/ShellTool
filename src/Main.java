import TelaPrincipal.TelaPrincipal;
import app.Atualizacao;
import app.VerificadorAtualizacao;
import app.Versao;
import model.Comando;
import model.Plataforma;

import javax.swing.*;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");

        SwingUtilities.invokeLater(() -> new TelaPrincipal().setVisible(true));

        new Thread(() -> {
            Atualizacao nova = VerificadorAtualizacao.buscarAtualizacao();
            if (nova != null) {
                SwingUtilities.invokeLater(() -> oferecerAtualizacao(nova));
            }
        }).start();
    }

    private static void oferecerAtualizacao(Atualizacao nova) {
        int escolha = JOptionPane.showConfirmDialog(null,
                "Versão " + nova.versao() + " disponível (atual: " + Versao.ATUAL + ").\nDeseja atualizar agora?",
                "Atualização", JOptionPane.YES_NO_OPTION);
        if (escolha != JOptionPane.YES_OPTION) {
            return;
        }

        JDialog aguarde = new JOptionPane("Baixando atualização...", JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[]{}).createDialog("Atualização");
        aguarde.setModal(false);
        aguarde.setVisible(true);

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return VerificadorAtualizacao.baixarInstalador(nova);
            }

            @Override
            protected void done() {
                aguarde.dispose();
                try {
                    Path instalador = get();
                    new ProcessBuilder("cmd", "/c", "start", "", instalador.toString())
                            .directory(instalador.getParent().toFile())
                            .start();
                    System.exit(0);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Falha ao atualizar: " + e.getMessage(),
                            "Atualização", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
