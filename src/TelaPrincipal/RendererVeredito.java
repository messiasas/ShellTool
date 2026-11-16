package TelaPrincipal;

import avaliacao.Veredito;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

// A JTable usa UM componente como carimbo para todas as células: as cores são redefinidas a cada chamada
public class RendererVeredito extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable tabela, Object valor, boolean selecionado,
                                                   boolean temFoco, int linha, int coluna) {
        super.getTableCellRendererComponent(tabela, valor, selecionado, temFoco, linha, coluna);

        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.BOLD));

        if (valor instanceof Veredito) {
            Veredito v = (Veredito) valor;
            setBackground(v.getCor());
            setForeground(Color.WHITE);
        } else {
            setBackground(tabela.getBackground());
            setForeground(tabela.getForeground());
        }
        return this;
    }
}
