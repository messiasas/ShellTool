package TelaPrincipal;

import model.Comando;
import model.Plataforma;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.List;

public class TelaPrincipal extends JFrame{

    private JComboBox<Plataforma> boxPlataforma;
    private DefaultListModel<Comando> modeloLista; // guarda os comandos no objeto
    private JList<Comando> listaComandos;
    private List<Comando> todosComandos = new ArrayList<>();

    public TelaPrincipal(){
        setTitle("BashTool");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // topo: label + setinha
        JPanel painelTopo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelTopo.add(new JLabel("Plataforma"));
        boxPlataforma = new JComboBox<>(Plataforma.values());
        painelTopo.add(boxPlataforma);
        add(painelTopo, BorderLayout.NORTH);

        // centro: lista de comandos
        modeloLista = new DefaultListModel<>();
        listaComandos = new JList<>(modeloLista);
        add(new JScrollPane(listaComandos), BorderLayout.CENTER);

        carregarComandosExemplo();
        atualizarLista();

        boxPlataforma.addActionListener(e -> atualizarLista());
    }

    private void carregarComandosExemplo() {
        todosComandos.add(new Comando("Endereços IP", Plataforma.PROLIN,
                "ip addr show", "Mostra os IPs de cada interface"));
        todosComandos.add(new Comando("Rotas IPv6", Plataforma.PROLIN,
                "ip -6 route show", "Verifica se existe rota default IPv6"));
        todosComandos.add(new Comando("Servidores DNS", Plataforma.PROLIN,
                "cat /etc/resolv.conf", "Mostra os DNS recebidos da rede"));
        todosComandos.add(new Comando("Ping IPv4 Google", Plataforma.PROLIN,
                "ping -c 3 8.8.8.8", "Testa conectividade IPv4 sem DNS"));
        todosComandos.add(new Comando("Ping IPv6 pelo LTE", Plataforma.PROLIN,
                "ping6 -I seth_lte0 -c 3 2001:4860:4860::8888", "Testa IPv6 pela rede celular"));
        todosComandos.add(new Comando("Teste NAT64", Plataforma.PROLIN,
                "ping6 -c 3 64:ff9b::808:808", "Alcança o 8.8.8.8 via NAT64"));

        todosComandos.add(new Comando("Endereços IP", Plataforma.ANDROID,
                "ip addr show", "Mostra IPs e interface v4- do CLAT"));
        todosComandos.add(new Comando("Ping IPv6 Google", Plataforma.ANDROID,
                "ping6 -c 3 2001:4860:4860::8888", "Testa conectividade IPv6 sem DNS"));
    }
    private void atualizarLista(){
        Plataforma escolhida = (Plataforma) boxPlataforma.getSelectedItem();
        modeloLista.clear();

        for(Comando c: todosComandos){
            if(c.getPlataforma() == escolhida){
                modeloLista.addElement(c);
            }
        }
    }
}









