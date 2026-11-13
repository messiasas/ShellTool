package TelaPrincipal;

import avaliacao.Veredito;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/*
 * Renderer = o "pintor" de uma célula da JTable.
 * A JTable não cria um componente por célula: ela usa UM componente (um JLabel)
 * como carimbo, configura com o valor da célula e pinta. Por isso, toda vez
 * que este método é chamado, precisamos definir TODAS as cores de novo.
 */
public class RendererVeredito extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable tabela, Object valor, boolean selecionado,
                                                   boolean temFoco, int linha, int coluna) {
        // o super configura o texto, a fonte, a borda de foco etc.
        super.getTableCellRendererComponent(tabela, valor, selecionado, temFoco, linha, coluna);

        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.BOLD));

        if (valor instanceof Veredito) {
            Veredito v = (Veredito) valor;
            setBackground(v.getCor());   // fundo com a cor do veredito
            setForeground(Color.WHITE);  // texto branco: legível sobre as cores escuras
        } else {
            setBackground(tabela.getBackground());
            setForeground(tabela.getForeground());
        }
        return this;
    }
}
