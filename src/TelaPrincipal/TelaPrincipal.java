package TelaPrincipal;

import avaliacao.AvaliadorResultado;
import avaliacao.ExecucaoAvaliada;
import avaliacao.Regra;
import avaliacao.RegistroTeste;
import avaliacao.ResultadoAvaliado;
import avaliacao.Veredito;
import conexao.ColetorRetorno;
import conexao.ConexaoAdb;
import conexao.ConexaoTelnet;
import conexao.MontadorComandoAT;
import model.Comando;
import model.Plataforma;
import model.ResultadoExecucao;
import model.StatusExecucao;
import model.TipoComando;
import persistencia.Configuracao;
import persistencia.RepositorioHistorico;
import persistencia.RepositorioTestes;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TelaPrincipal extends JFrame{

    private JComboBox<Plataforma> boxPlataforma;
    private DefaultListModel<Comando> modeloLista;
    private JList<Comando> listaComandos;
    private List<Comando> todosComandos = new ArrayList<>();

    private JTextField campoComando;
    private JTextArea areaDescricao;

    private JTextArea areaRetorno;
    private JButton botaoEnviar;
    private JButton botaoLimpar;
    private JButton botaoEditar;
    private JButton botaoExcluir;

    private JTextField campoHost;
    private JTextField campoPorta;
    private JButton botaoConectar;
    private JLabel labelStatus;
    private ConexaoTelnet conexao = new ConexaoTelnet();

    private JTextField campoLogin;
    private boolean loginEnviado = false;
    private String enderecoConectado = "";

    private Configuracao configuracao = new Configuracao();

    private CardLayout cartoesConexao;
    private JPanel painelConexao;

    private ConexaoAdb conexaoAdb;
    private JComboBox<String> boxDispositivo;
    private JButton botaoAtualizar;
    private JLabel labelStatusAndroid;
    private boolean listandoDispositivos = false;

    private boolean adbEmAndamento = false;
    private int numeroAdbAtual;
    private String textoAdbAtual;
    private long inicioAdbNanos;
    private final StringBuilder saidaAdb = new StringBuilder();

    private JTextField campoLivre;
    private JButton botaoEnviarLivre;
    private JButton botaoSalvarTeste;
    private JCheckBox caixaAt;

    private RepositorioTestes repositorio = new RepositorioTestes();
    private boolean arquivoCarregado = false;

    private static final int TEMPO_LIMITE_MS = 30_000;

    private ColetorRetorno coletor = new ColetorRetorno();
    private int contadorExecucao = 0;
    private Timer timerLimite;
    private JButton botaoCancelar;
    private ResultadoExecucao ultimoResultado;

    private Comando execucaoAtualTeste;
    private TipoComando execucaoAtualTipo = TipoComando.SHELL;
    private String execucaoAtualNome;
    private String execucaoAtualIdentificacao;
    private Comando ultimoTeste;

    private AvaliadorResultado avaliador = new AvaliadorResultado();
    private JButton botaoCriarRegra;
    private DefaultTableModel modeloResultados;
    private JTable tabelaResultados;
    private List<ExecucaoAvaliada> linhasResultados = new ArrayList<>();
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private RepositorioHistorico repositorioHistorico = new RepositorioHistorico();
    private RegistroTeste registroAtual;
    private JButton botaoRegistrar;
    private JButton botaoEncerrar;
    private JButton botaoLimparResultados;
    private JLabel labelGravando;
    private JLabel labelPnSn;

    private Deque<ComandoPendente> filaComandos = new ArrayDeque<>();

    private static class ComandoPendente {
        final Plataforma plataforma;
        final String serial;
        final String texto;
        final String identificacao;

        ComandoPendente(Plataforma plataforma, String serial, String texto, String identificacao) {
            this.plataforma = plataforma;
            this.serial = serial;
            this.texto = texto;
            this.identificacao = identificacao;
        }
    }

    public TelaPrincipal(){
        setTitle("ShellTool");
        setSize(1280, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        carregarConfiguracao();
        conexaoAdb = new ConexaoAdb(configuracao.getAdb());
        // Ao fechar o programa, não deixa um adb rodando sozinho
        Runtime.getRuntime().addShutdownHook(new Thread(conexaoAdb::cancelar));

        JPanel painelTopo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelTopo.add(new JLabel("Plataforma"));
        boxPlataforma = new JComboBox<>(Plataforma.values());
        painelTopo.add(boxPlataforma);

        painelTopo.add(Box.createHorizontalStrut(20));

        JPanel cartaoProlin = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cartaoProlin.add(new JLabel("IP:"));
        campoHost = new JTextField(15);
        cartaoProlin.add(campoHost);

        cartaoProlin.add(new JLabel("Porta:"));
        campoPorta = new JTextField("2323", 5);
        cartaoProlin.add(campoPorta);

        cartaoProlin.add(new JLabel("Login:"));
        campoLogin = new JTextField(10);
        cartaoProlin.add(campoLogin);

        botaoConectar = new JButton("Conectar");
        cartaoProlin.add(botaoConectar);

        labelStatus = new JLabel("Desconectado");
        labelStatus.setForeground(Color.RED);
        cartaoProlin.add(labelStatus);

        JPanel cartaoAndroid = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cartaoAndroid.add(new JLabel("Dispositivo:"));
        boxDispositivo = new JComboBox<>();
        boxDispositivo.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXXX");
        cartaoAndroid.add(boxDispositivo);
        botaoAtualizar = new JButton("Atualizar");
        cartaoAndroid.add(botaoAtualizar);
        labelStatusAndroid = new JLabel(" ");
        cartaoAndroid.add(labelStatusAndroid);

        // CardLayout: um cartão por plataforma no mesmo lugar; show(painel, plataforma.name()) troca o visível
        cartoesConexao = new CardLayout();
        painelConexao = new JPanel(cartoesConexao);
        painelConexao.add(cartaoProlin, Plataforma.PROLIN.name());
        painelConexao.add(cartaoAndroid, Plataforma.ANDROID.name());
        painelTopo.add(painelConexao);

        add(painelTopo, BorderLayout.NORTH);

        modeloLista = new DefaultListModel<>();
        listaComandos = new JList<>(modeloLista);
        JScrollPane scrollLista = new JScrollPane(listaComandos);
        scrollLista.setPreferredSize(new Dimension(180, 0));
        add(scrollLista, BorderLayout.WEST);

        JPanel painelDetalhes = new JPanel();
        painelDetalhes.setLayout(new BoxLayout(painelDetalhes, BoxLayout.Y_AXIS));
        painelDetalhes.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        painelDetalhes.add(new JLabel("Comando:"));
        campoComando = new JTextField();
        campoComando.setEditable(false);
        campoComando.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        campoComando.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        campoComando.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelDetalhes.add(campoComando);

        painelDetalhes.add(Box.createVerticalStrut(10));

        painelDetalhes.add(new JLabel("Descrição:"));
        areaDescricao = new JTextArea(3, 30);
        areaDescricao.setEditable(false);
        areaDescricao.setLineWrap(true);
        areaDescricao.setWrapStyleWord(true);
        JScrollPane scrollDescricao = new JScrollPane(areaDescricao);
        scrollDescricao.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelDetalhes.add(scrollDescricao);

        scrollDescricao.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        painelDetalhes.add(Box.createVerticalStrut(10));

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        botaoEnviar = new JButton("Enviar");
        botaoLimpar = new JButton("Limpar retorno");
        painelBotoes.add(botaoEnviar);
        painelBotoes.add(Box.createHorizontalStrut(10));
        botaoCancelar = new JButton("Cancelar");
        botaoCancelar.setEnabled(false);
        painelBotoes.add(botaoCancelar);
        painelBotoes.add(Box.createHorizontalStrut(10));
        painelBotoes.add(botaoLimpar);
        botaoEditar = new JButton("Editar teste");
        painelBotoes.add(Box.createHorizontalStrut(10));
        painelBotoes.add(botaoEditar);
        botaoExcluir = new JButton("Excluir teste");
        painelBotoes.add(Box.createHorizontalStrut(10));
        painelBotoes.add(botaoExcluir);
        painelBotoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelBotoes.setMaximumSize(new Dimension(Integer.MAX_VALUE, painelBotoes.getPreferredSize().height));
        painelDetalhes.add(painelBotoes);

        painelDetalhes.add(Box.createVerticalStrut(10));

        JPanel painelRetorno = new JPanel(new BorderLayout(0, 4));
        JPanel topoRetorno = new JPanel(new BorderLayout());
        topoRetorno.add(new JLabel("Retorno:"), BorderLayout.WEST);
        botaoCriarRegra = new JButton("Criar regra da seleção");
        topoRetorno.add(botaoCriarRegra, BorderLayout.EAST);
        painelRetorno.add(topoRetorno, BorderLayout.NORTH);

        areaRetorno = new JTextArea();
        areaRetorno.setEditable(false);
        areaRetorno.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scrollRetorno = new JScrollPane(areaRetorno);
        painelRetorno.add(scrollRetorno, BorderLayout.CENTER);

        JPanel painelResultados = criarPainelResultados();

        painelRetorno.setPreferredSize(new Dimension(400, 300));
        painelResultados.setPreferredSize(new Dimension(400, 300));
        painelRetorno.setMinimumSize(new Dimension(150, 100));
        painelResultados.setMinimumSize(new Dimension(150, 100));

        JSplitPane divisao = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, painelRetorno, painelResultados);
        divisao.setResizeWeight(0.5);
        divisao.setContinuousLayout(true);
        divisao.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelDetalhes.add(divisao);
        SwingUtilities.invokeLater(() -> divisao.setDividerLocation(0.5));

        JPanel painelInferior = new JPanel(new BorderLayout(8, 0));
        painelInferior.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        painelInferior.add(new JLabel("Comando livre:"), BorderLayout.WEST);

        campoLivre = new JTextField();
        campoLivre.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        painelInferior.add(campoLivre, BorderLayout.CENTER);

        JPanel painelBotoesLivre = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        caixaAt = new JCheckBox("AT");
        painelBotoesLivre.add(caixaAt);
        painelBotoesLivre.add(Box.createHorizontalStrut(10));
        botaoEnviarLivre = new JButton("Enviar livre");
        botaoSalvarTeste = new JButton("Salvar como teste");
        painelBotoesLivre.add(botaoEnviarLivre);
        painelBotoesLivre.add(Box.createHorizontalStrut(10));
        painelBotoesLivre.add(botaoSalvarTeste);
        painelInferior.add(painelBotoesLivre, BorderLayout.EAST);

        JPanel painelCentral = new JPanel(new BorderLayout());
        painelCentral.add(painelDetalhes, BorderLayout.CENTER);
        painelCentral.add(painelInferior, BorderLayout.SOUTH);
        add(painelCentral, BorderLayout.CENTER);

        carregarTestes();
        atualizarLista();

        boxPlataforma.addActionListener(e -> aoTrocarPlataforma());
        botaoAtualizar.addActionListener(e -> atualizarDispositivos());
        listaComandos.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrarDetalhes();
            }
        });
        botaoEnviar.addActionListener(e -> enviarComando());
        botaoLimpar.addActionListener(e -> areaRetorno.setText(""));
        botaoConectar.addActionListener(e -> alternarConexao());

        botaoEnviarLivre.addActionListener(e -> enviarComandoLivre());
        campoLivre.addActionListener(e -> enviarComandoLivre());
        botaoSalvarTeste.addActionListener(e -> salvarComoTeste());

        botaoEditar.addActionListener(e -> editarTeste());
        botaoExcluir.addActionListener(e -> excluirTeste());
        listaComandos.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    excluirTeste();
                }
            }
        });

        botaoCancelar.addActionListener(e -> cancelarExecucao());

        timerLimite = new Timer(TEMPO_LIMITE_MS, e -> finalizarExecucao(StatusExecucao.TEMPO_ESGOTADO));
        timerLimite.setRepeats(false);

        botaoCriarRegra.addActionListener(e -> criarRegraDaSelecao());
        botaoLimparResultados.addActionListener(e -> limparResultados());
        botaoRegistrar.addActionListener(e -> registrarTeste());
        botaoEncerrar.addActionListener(e -> encerrarRegistro());

        tabelaResultados.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int linha = tabelaResultados.rowAtPoint(e.getPoint());
                    if (linha >= 0) {
                        mostrarDetalhesExecucao(linhasResultados.get(tabelaResultados.convertRowIndexToModel(linha)));
                    }
                }
            }
        });

        atualizarBotoesExecucao();
        atualizarDicaAt();
    }

    private void carregarConfiguracao() {
        try {
            configuracao.carregar();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao ler o arquivo de configuração:\n" + configuracao.getArquivo()
                            + "\n\n" + ex.getMessage() + "\n\nSerão usados os valores padrão.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Plataforma plataformaSelecionada() {
        return (Plataforma) boxPlataforma.getSelectedItem();
    }

    private void aoTrocarPlataforma() {
        atualizarLista();
        Plataforma plataforma = plataformaSelecionada();
        cartoesConexao.show(painelConexao, plataforma.name());
        atualizarDicaAt();
        if (plataforma == Plataforma.ANDROID) {
            atualizarDispositivos();
        }
    }

    private void atualizarDicaAt() {
        caixaAt.setToolTipText("Envia o texto como comando AT pela porta " + portaAt(plataformaSelecionada()));
    }

    private String portaAt(Plataforma plataforma) {
        if (plataforma == Plataforma.PROLIN) {
            return MontadorComandoAT.PORTA_AT;
        }
        String porta = configuracao.getPortaAtAndroid();
        return porta.isEmpty() ? "(não configurada no config.txt)" : porta;
    }

    private void atualizarDispositivos() {
        if (listandoDispositivos) {
            return;
        }
        listandoDispositivos = true;
        botaoAtualizar.setEnabled(false);
        labelStatusAndroid.setText("Procurando...");
        labelStatusAndroid.setForeground(Color.ORANGE);

        // Rede/processo fora da EDT; a tela só é atualizada via invokeLater
        new Thread(() -> {
            try {
                ConexaoAdb.Dispositivos d = conexaoAdb.listarDispositivos();
                SwingUtilities.invokeLater(() -> mostrarDispositivos(d));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    terminarListagem();
                    boxDispositivo.removeAllItems();
                    atualizarStatusAndroid(false, "adb não encontrado");
                    JOptionPane.showMessageDialog(this, mensagemAdbNaoEncontrado(ex),
                            "adb não encontrado", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void terminarListagem() {
        listandoDispositivos = false;
        botaoAtualizar.setEnabled(true);
    }

    private void mostrarDispositivos(ConexaoAdb.Dispositivos d) {
        terminarListagem();
        String anterior = serialSelecionado();

        boxDispositivo.removeAllItems();
        for (String serial : d.prontos) {
            boxDispositivo.addItem(serial);
        }
        if (d.prontos.size() == 1) {
            boxDispositivo.setSelectedIndex(0);
        } else if (anterior != null && d.prontos.contains(anterior)) {
            boxDispositivo.setSelectedItem(anterior);
        } else {
            boxDispositivo.setSelectedIndex(-1);
        }

        if (!d.prontos.isEmpty()) {
            atualizarStatusAndroid(true, "Pronto");
        } else if (!d.naoAutorizados.isEmpty()) {
            atualizarStatusAndroid(false, "Não autorizado: aceite a depuração USB no aparelho");
        } else if (!d.offline.isEmpty()) {
            atualizarStatusAndroid(false, "Offline");
        } else {
            atualizarStatusAndroid(false, "Nenhum dispositivo");
        }
    }

    private void atualizarStatusAndroid(boolean pronto, String texto) {
        labelStatusAndroid.setText(texto);
        labelStatusAndroid.setForeground(pronto ? new Color(0, 128, 0) : Color.RED);
    }

    private String serialSelecionado() {
        return (String) boxDispositivo.getSelectedItem();
    }

    private String mensagemAdbNaoEncontrado(IOException ex) {
        return "Não foi possível executar o adb (\"" + configuracao.getAdb() + "\").\n\n"
                + "Ajuste a chave \"adb\" no arquivo:\n" + configuracao.getArquivo()
                + "\ne reabra o programa.\n\nDetalhe: " + ex.getMessage();
    }

    private boolean destinoPronto(Plataforma plataforma) {
        if (plataforma == Plataforma.ANDROID) {
            if (serialSelecionado() == null) {
                JOptionPane.showMessageDialog(this,
                        "Selecione um dispositivo Android (use \"Atualizar\" para procurar).");
                return false;
            }
            return true;
        }
        if (!conexao.estaConectado()) {
            JOptionPane.showMessageDialog(this, "Conecte-se ao terminal primeiro.");
            return false;
        }
        return true;
    }

    private boolean emExecucao() {
        return coletor.emAndamento() || adbEmAndamento;
    }

    private JPanel criarPainelResultados() {
        JPanel painel = new JPanel(new BorderLayout(0, 4));

        JPanel topo = new JPanel();
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));

        JLabel titulo = new JLabel("Resultados:");
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(titulo);
        topo.add(Box.createVerticalStrut(4));

        JPanel linhaBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        botaoRegistrar = new JButton("Registrar teste");
        botaoEncerrar = new JButton("Encerrar");
        botaoLimparResultados = new JButton("Limpar resultados");
        labelGravando = new JLabel("● Gravando");
        labelGravando.setForeground(Color.RED);
        labelGravando.setFont(labelGravando.getFont().deriveFont(Font.BOLD));
        labelGravando.setVisible(false);

        linhaBotoes.add(botaoRegistrar);
        linhaBotoes.add(Box.createHorizontalStrut(10));
        linhaBotoes.add(botaoEncerrar);
        linhaBotoes.add(Box.createHorizontalStrut(10));
        linhaBotoes.add(botaoLimparResultados);
        linhaBotoes.add(Box.createHorizontalStrut(10));
        linhaBotoes.add(labelGravando);
        linhaBotoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(linhaBotoes);

        labelPnSn = new JLabel();
        labelPnSn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        labelPnSn.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelPnSn.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        labelPnSn.setVisible(false);
        topo.add(labelPnSn);

        painel.add(topo, BorderLayout.NORTH);

        modeloResultados = new DefaultTableModel(new Object[]{"Hora", "Teste", "Veredito", "Detalhe"}, 0) {
            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
        tabelaResultados = new JTable(modeloResultados);
        tabelaResultados.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabelaResultados.setRowHeight(22);
        tabelaResultados.getColumnModel().getColumn(2).setCellRenderer(new RendererVeredito());
        tabelaResultados.getColumnModel().getColumn(0).setPreferredWidth(70);
        tabelaResultados.getColumnModel().getColumn(1).setPreferredWidth(150);
        tabelaResultados.getColumnModel().getColumn(2).setPreferredWidth(80);
        tabelaResultados.getColumnModel().getColumn(3).setPreferredWidth(250);

        painel.add(new JScrollPane(tabelaResultados), BorderLayout.CENTER);
        return painel;
    }

    private void carregarTestes() {
        try {
            todosComandos = repositorio.carregar();
            arquivoCarregado = true;
        } catch (IOException ex) {
            todosComandos = new ArrayList<>();
            JOptionPane.showMessageDialog(this,
                    "Erro ao ler o arquivo de testes:\n" + repositorio.getArquivo()
                            + "\n\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
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
    private void mostrarDetalhes() {
        Comando selecionado = listaComandos.getSelectedValue();

        if (selecionado == null) {
            campoComando.setText("");
            areaDescricao.setText("");
            return;
        }

        campoComando.setText(selecionado.isAt()
                ? MontadorComandoAT.textoExibicao(selecionado.getTxtComando(), portaAt(selecionado.getPlataforma()))
                : selecionado.getTxtComando());
        areaDescricao.setText(selecionado.getDescricao());
    }
    private void enviarComando() {
        if (emExecucao()) {
            return;
        }

        Comando selecionado = listaComandos.getSelectedValue();

        if (selecionado == null) {
            JOptionPane.showMessageDialog(this, "Selecione um comando na lista.");
            return;
        }

        Plataforma plataforma = selecionado.getPlataforma();
        if (!destinoPronto(plataforma)) {
            return;
        }

        executar(plataforma, serialSelecionado(), selecionado.getTxtComando(), selecionado.getTipo(),
                selecionado.getEsperaSegundos(), selecionado, selecionado.getNome(), null);
    }

    private void enviarComandoLivre() {
        if (emExecucao()) {
            return;
        }

        String texto = campoLivre.getText().trim();

        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Digite um comando no campo livre.");
            return;
        }

        Plataforma plataforma = plataformaSelecionada();
        if (!destinoPronto(plataforma)) {
            return;
        }

        TipoComando tipo = caixaAt.isSelected() ? TipoComando.AT : TipoComando.SHELL;
        if (executar(plataforma, serialSelecionado(), texto, tipo, Comando.ESPERA_PADRAO,
                null, "Livre: " + cortar(texto, 40), null)) {
            campoLivre.setText("");
        }
    }

    private boolean executar(Plataforma plataforma, String serial, String textoComando, TipoComando tipo,
                             int esperaSegundos, Comando teste, String nomeExibicao, String identificacao) {
        if (plataforma == Plataforma.ANDROID) {
            return executarAdb(serial, textoComando, tipo, esperaSegundos, teste, nomeExibicao, identificacao);
        }
        return executarTelnet(textoComando, tipo, esperaSegundos, teste, nomeExibicao, identificacao);
    }

    private boolean executarTelnet(String textoComando, TipoComando tipo, int esperaSegundos,
                                   Comando teste, String nomeExibicao, String identificacao) {
        int numero = ++contadorExecucao;

        String linhaShell = textoComando;
        String textoExibido = textoComando;
        int tempoLimite = TEMPO_LIMITE_MS;
        if (tipo == TipoComando.AT) {
            linhaShell = MontadorComandoAT.montarLinha(textoComando, esperaSegundos);
            textoExibido = MontadorComandoAT.textoExibicao(textoComando);
            tempoLimite += (1 + esperaSegundos) * 1000;
        }
        String linha = ColetorRetorno.montarLinhaComMarcador(linhaShell, numero);

        garantirQuebraDeLinha();
        exibirTexto("> " + textoExibido + "\n");

        try {
            conexao.enviar(linha);
        } catch (IOException ex) {
            exibirTexto("[Erro ao enviar: " + ex.getMessage() + "]\n");
            return false;
        }

        // Iniciar depois de enviar é seguro: a resposta só é tratada na EDT, que está ocupada aqui
        coletor.iniciar(numero, textoExibido);
        execucaoAtualTeste = teste;
        execucaoAtualTipo = tipo;
        execucaoAtualNome = nomeExibicao;
        execucaoAtualIdentificacao = identificacao;
        timerLimite.setInitialDelay(tempoLimite);
        timerLimite.restart();
        atualizarBotoesExecucao();
        return true;
    }

    private boolean executarAdb(String serial, String textoComando, TipoComando tipo, int esperaSegundos,
                                Comando teste, String nomeExibicao, String identificacao) {
        String linhaShell = textoComando;
        String textoExibido = textoComando;
        int tempoLimite = TEMPO_LIMITE_MS;
        if (tipo == TipoComando.AT) {
            String porta = configuracao.getPortaAtAndroid();
            if (porta.isEmpty()) {
                registrarAtNaoConfigurado(textoComando, nomeExibicao);
                return false;
            }
            linhaShell = MontadorComandoAT.montarLinha(textoComando, esperaSegundos, porta);
            if (configuracao.isSuAndroid()) {
                linhaShell = MontadorComandoAT.envolverEmSu(linhaShell);
            }
            textoExibido = MontadorComandoAT.textoExibicao(textoComando, porta);
            tempoLimite += (1 + esperaSegundos) * 1000;
        }

        int numero = ++contadorExecucao;
        garantirQuebraDeLinha();
        exibirTexto("> " + textoExibido + "\n");

        try {
            // Os avisos chegam na thread do adb; invokeLater os leva para a EDT
            conexaoAdb.executar(serial, linhaShell,
                    texto -> SwingUtilities.invokeLater(() -> receberAdb(numero, texto)),
                    codigo -> SwingUtilities.invokeLater(() -> terminarAdb(numero, codigo)));
        } catch (IOException ex) {
            exibirTexto("[Erro ao executar o adb: " + ex.getMessage() + "]\n");
            JOptionPane.showMessageDialog(this, mensagemAdbNaoEncontrado(ex),
                    "adb não encontrado", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        adbEmAndamento = true;
        numeroAdbAtual = numero;
        textoAdbAtual = textoExibido;
        inicioAdbNanos = System.nanoTime();
        saidaAdb.setLength(0);
        execucaoAtualTeste = teste;
        execucaoAtualTipo = tipo;
        execucaoAtualNome = nomeExibicao;
        execucaoAtualIdentificacao = identificacao;
        timerLimite.setInitialDelay(tempoLimite);
        timerLimite.restart();
        atualizarBotoesExecucao();
        return true;
    }

    private void receberAdb(int numero, String texto) {
        // Aviso atrasado de uma execução já encerrada (cancelada/tempo esgotado): descarta
        if (!adbEmAndamento || numero != numeroAdbAtual) {
            return;
        }
        saidaAdb.append(texto);
        exibirTexto(texto);
    }

    private void terminarAdb(int numero, Integer codigo) {
        // Aviso atrasado de uma execução já encerrada (cancelada/tempo esgotado): descarta
        if (!adbEmAndamento || numero != numeroAdbAtual) {
            return;
        }
        finalizarAdb(StatusExecucao.CONCLUIDO, codigo);
    }

    private void finalizarAdb(StatusExecucao status, Integer codigo) {
        adbEmAndamento = false;
        long duracaoMs = (System.nanoTime() - inicioAdbNanos) / 1_000_000;
        encerrarExecucao(new ResultadoExecucao(textoAdbAtual, saidaAdb.toString(), codigo, duracaoMs, status));
    }

    private void registrarAtNaoConfigurado(String comandoAt, String nomeExibicao) {
        garantirQuebraDeLinha();
        exibirTexto("> " + comandoAt.trim() + "\n[AT não configurado]\n");

        ResultadoExecucao resultado = new ResultadoExecucao(comandoAt.trim(), "", null, 0, StatusExecucao.CONCLUIDO);
        ExecucaoAvaliada execucao = new ExecucaoAvaliada(LocalTime.now().withNano(0), nomeExibicao,
                resultado, new ResultadoAvaliado(Veredito.INFO, "AT não configurado"));
        adicionarNaTabela(execucao);
        if (registroAtual != null) {
            registroAtual.adicionar(execucao);
        }

        JOptionPane.showMessageDialog(this,
                "Comandos AT no Android exigem root e a porta serial do modem,\n"
                        + "que varia por fabricante (ex.: /dev/smd11, /dev/ttyUSB2...).\n\n"
                        + "Configure a chave \"porta_at_android\" (e, se preciso, su_android=sim) em:\n"
                        + configuracao.getArquivo() + "\ne reabra o programa.",
                "AT no Android não configurado", JOptionPane.WARNING_MESSAGE);
    }

    private void cancelarExecucao() {
        if (adbEmAndamento) {
            conexaoAdb.cancelar();
            finalizarAdb(StatusExecucao.CANCELADO, null);
            return;
        }
        if (!coletor.emAndamento()) {
            return;
        }
        try {
            conexao.enviarBytes(new byte[]{0x03});
        } catch (IOException ex) {
            exibirTexto("\n[Erro ao enviar Ctrl+C: " + ex.getMessage() + "]\n");
        }
        finalizarExecucao(StatusExecucao.CANCELADO);
    }

    private void finalizarExecucao(StatusExecucao status) {
        if (adbEmAndamento) {
            if (status == StatusExecucao.TEMPO_ESGOTADO) {
                conexaoAdb.cancelar();
                finalizarAdb(status, null);
            }
            return;
        }
        ResultadoExecucao resultado = coletor.finalizar(status, this::exibirTexto);
        if (resultado != null) {
            encerrarExecucao(resultado);
        }
    }

    private void encerrarExecucao(ResultadoExecucao resultado) {
        timerLimite.stop();
        ultimoResultado = resultado;
        ultimoTeste = execucaoAtualTeste;
        aoConcluirExecucao(resultado);
        atualizarBotoesExecucao();

        if (!filaComandos.isEmpty()) {
            SwingUtilities.invokeLater(this::executarProximoDaFila);
        }
    }

    // Fila: uma execução por vez; encerrarExecucao() chama o próximo da fila
    private void executarProximoDaFila() {
        if (emExecucao() || filaComandos.isEmpty()) {
            return;
        }
        if (filaComandos.peek().plataforma == Plataforma.PROLIN && !conexao.estaConectado()) {
            filaComandos.clear();
            atualizarBotoesExecucao();
            return;
        }
        ComandoPendente proximo = filaComandos.poll();
        if (!executar(proximo.plataforma, proximo.serial, proximo.texto, TipoComando.SHELL,
                Comando.ESPERA_PADRAO, null, proximo.identificacao, proximo.identificacao)) {
            filaComandos.clear();
            atualizarBotoesExecucao();
        }
    }

    private void aoConcluirExecucao(ResultadoExecucao resultado) {
        String rodape;
        switch (resultado.getStatus()) {
            case CONCLUIDO:
                String segundos = RepositorioHistorico.segundos(resultado.getDuracaoMs());
                rodape = "[fim | código de saída " + resultado.getCodigoSaida() + " | " + segundos + " s]";
                break;
            case TEMPO_ESGOTADO:
                rodape = "[tempo esgotado]";
                break;
            case CANCELADO:
                rodape = "[cancelado]";
                break;
            default:
                rodape = "[conexão perdida]";
                break;
        }
        garantirQuebraDeLinha();
        exibirTexto(rodape + "\n");

        ResultadoAvaliado avaliacao;
        if (execucaoAtualIdentificacao != null) {
            String valor = extrairIdentificacao(resultado);
            avaliacao = new ResultadoAvaliado(Veredito.INFO, valor);
            if (registroAtual != null) {
                if (execucaoAtualIdentificacao.equals("PN")) {
                    registroAtual.setPn(valor);
                } else {
                    registroAtual.setSn(valor);
                }
            }
        } else {
            avaliacao = avaliador.avaliar(execucaoAtualTeste, execucaoAtualTipo, resultado);
        }

        ExecucaoAvaliada execucao = new ExecucaoAvaliada(LocalTime.now().withNano(0),
                execucaoAtualNome, resultado, avaliacao);
        adicionarNaTabela(execucao);
        if (registroAtual != null) {
            registroAtual.adicionar(execucao);
        }
    }

    private String extrairIdentificacao(ResultadoExecucao resultado) {
        Integer codigo = resultado.getCodigoSaida();
        if (resultado.getStatus() != StatusExecucao.CONCLUIDO || codigo == null || codigo != 0) {
            return RegistroTeste.NAO_IDENTIFICADO;
        }
        String valor = resultado.getSaida().trim();
        int quebra = valor.indexOf('\n');
        if (quebra >= 0) {
            valor = valor.substring(0, quebra).trim();
        }
        return valor.isEmpty() ? RegistroTeste.NAO_IDENTIFICADO : valor;
    }

    private void atualizarBotoesExecucao() {
        boolean emExecucao = emExecucao();
        boolean gravando = registroAtual != null;

        botaoEnviar.setEnabled(!emExecucao);
        botaoEnviarLivre.setEnabled(!emExecucao);
        botaoCancelar.setEnabled(emExecucao);
        botaoRegistrar.setEnabled(!gravando);
        botaoEncerrar.setEnabled(gravando && !emExecucao && filaComandos.isEmpty());
        atualizarLabelPnSn();
    }

    private void atualizarLabelPnSn() {
        if (registroAtual == null) {
            return;
        }
        boolean aguardando = emExecucao() || !filaComandos.isEmpty();
        labelPnSn.setText("PN: " + textoIdentificacao(registroAtual.getPn(), aguardando)
                + "  |  SN: " + textoIdentificacao(registroAtual.getSn(), aguardando));
    }

    private String textoIdentificacao(String valor, boolean aguardando) {
        if (valor != null) {
            return valor;
        }
        return aguardando ? "aguardando..." : RegistroTeste.NAO_IDENTIFICADO;
    }

    private void adicionarNaTabela(ExecucaoAvaliada execucao) {
        linhasResultados.add(execucao);
        modeloResultados.addRow(new Object[]{
                execucao.getHora().format(FORMATO_HORA),
                execucao.getNomeTeste(),
                execucao.getAvaliacao().getVeredito(),
                execucao.getAvaliacao().getDetalhe()
        });
        int ultima = modeloResultados.getRowCount() - 1;
        tabelaResultados.scrollRectToVisible(tabelaResultados.getCellRect(ultima, 0, true));
    }

    private void limparResultados() {
        modeloResultados.setRowCount(0);
        linhasResultados.clear();
    }

    private void mostrarDetalhesExecucao(ExecucaoAvaliada execucao) {
        ResultadoExecucao r = execucao.getResultado();
        ResultadoAvaliado a = execucao.getAvaliacao();
        Integer codigo = r.getCodigoSaida();

        JPanel info = new JPanel(new GridLayout(0, 1, 0, 2));
        info.add(new JLabel("Teste: " + execucao.getNomeTeste()
                + "   (" + execucao.getHora().format(FORMATO_HORA) + ")"));

        JTextField campoTexto = new JTextField(r.getTextoComando());
        campoTexto.setEditable(false);
        campoTexto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        info.add(campoTexto);

        JLabel labelVeredito = new JLabel("Veredito: " + a.getVeredito() + " - " + a.getDetalhe());
        labelVeredito.setForeground(a.getVeredito().getCor());
        labelVeredito.setFont(labelVeredito.getFont().deriveFont(Font.BOLD));
        info.add(labelVeredito);

        info.add(new JLabel("Código de saída: " + (codigo == null ? "-" : codigo)
                + "   |   Duração: " + RepositorioHistorico.segundos(r.getDuracaoMs()) + " s"
                + "   |   Status: " + r.getStatus()));

        JTextArea areaSaida = new JTextArea(r.getSaida(), 18, 80);
        areaSaida.setEditable(false);
        areaSaida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        areaSaida.setCaretPosition(0);

        JPanel painel = new JPanel(new BorderLayout(0, 8));
        painel.add(info, BorderLayout.NORTH);
        painel.add(new JScrollPane(areaSaida), BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, painel, "Detalhes da execução", JOptionPane.PLAIN_MESSAGE);
    }

    private void criarRegraDaSelecao() {
        if (ultimoResultado == null) {
            JOptionPane.showMessageDialog(this, "Execute um teste primeiro.");
            return;
        }
        if (ultimoTeste == null) {
            JOptionPane.showMessageDialog(this,
                    "A última execução não foi de um teste salvo.\n"
                            + "Se foi um comando livre, use \"Salvar como teste\" e execute-o pela lista.");
            return;
        }
        if (!todosComandos.contains(ultimoTeste)) {
            JOptionPane.showMessageDialog(this,
                    "O teste da última execução foi excluído. Execute um teste novamente.");
            return;
        }
        if (!arquivoCarregado) {
            JOptionPane.showMessageDialog(this,
                    "O arquivo de testes não foi lido corretamente ao abrir o programa.\n"
                            + "Para não apagar os testes existentes, nada será salvo.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selecao = areaRetorno.getSelectedText();
        if (selecao == null || selecao.isBlank()) {
            JOptionPane.showMessageDialog(this, "Selecione um trecho na área de retorno.");
            return;
        }

        JComboBox<Veredito> boxTipo = new JComboBox<>(new Veredito[]{Veredito.OK, Veredito.ALERTA, Veredito.FALHA});
        JTextField campoPadrao = new JTextField(primeiraLinha(selecao), 35);
        campoPadrao.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JTextField campoRotulo = new JTextField(35);
        JCheckBox caixaRegex = new JCheckBox("Expressão regular");

        JPanel painel = new JPanel(new GridLayout(0, 1, 0, 4));
        painel.add(new JLabel("Teste: " + ultimoTeste.getNome() + " (" + ultimoTeste.getPlataforma() + ")"));
        painel.add(new JLabel("Tipo:"));
        painel.add(boxTipo);
        painel.add(new JLabel("Padrão (a saída deve conter):"));
        painel.add(campoPadrao);
        painel.add(new JLabel("Rótulo (opcional):"));
        painel.add(campoRotulo);
        painel.add(caixaRegex);

        String padrao;
        while (true) {
            int opcao = JOptionPane.showConfirmDialog(this, painel, "Criar regra",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                return;
            }
            padrao = campoPadrao.getText().trim();
            if (padrao.isEmpty()) {
                JOptionPane.showMessageDialog(this, "O padrão é obrigatório.");
                continue;
            }
            if (padrao.contains(" => ")) {
                JOptionPane.showMessageDialog(this, "O padrão não pode conter \" => \".");
                continue;
            }
            if (caixaRegex.isSelected()) {
                try {
                    Pattern.compile(padrao);
                } catch (PatternSyntaxException ex) {
                    JOptionPane.showMessageDialog(this, "Expressão regular inválida:\n" + ex.getDescription());
                    continue;
                }
            }
            break;
        }

        Regra regra = new Regra((Veredito) boxTipo.getSelectedItem(), padrao,
                campoRotulo.getText().trim(), caixaRegex.isSelected());
        ultimoTeste.adicionarRegra(regra);

        try {
            repositorio.salvar(todosComandos);
        } catch (IOException ex) {
            ultimoTeste.getRegras().remove(regra);
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o arquivo de testes:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Regra adicionada ao teste \"" + ultimoTeste.getNome() + "\":\n" + regra);
    }

    private String primeiraLinha(String texto) {
        for (String linha : texto.split("\n")) {
            if (!linha.isBlank()) {
                return linha.trim();
            }
        }
        return texto.trim();
    }

    private void registrarTeste() {
        if (registroAtual != null) {
            return;
        }
        Plataforma plataforma = plataformaSelecionada();
        if (!destinoPronto(plataforma)) {
            return;
        }
        if (emExecucao()) {
            JOptionPane.showMessageDialog(this, "Aguarde a execução atual terminar.");
            return;
        }

        boolean android = plataforma == Plataforma.ANDROID;
        String serial = android ? serialSelecionado() : null;
        registroAtual = new RegistroTeste(LocalDateTime.now().withNano(0), plataforma,
                android ? serial : enderecoConectado);
        labelGravando.setVisible(true);
        labelPnSn.setVisible(true);

        if (android) {
            filaComandos.add(new ComandoPendente(plataforma, serial, configuracao.getCmdSnAndroid(), "SN"));
            filaComandos.add(new ComandoPendente(plataforma, serial, configuracao.getCmdPnAndroid(), "PN"));
        } else {
            filaComandos.add(new ComandoPendente(plataforma, null, "/startup/bin/getprop ro.fac.pn", "PN"));
            filaComandos.add(new ComandoPendente(plataforma, null, "/startup/bin/getprop ro.fac.sn", "SN"));
        }
        atualizarBotoesExecucao();
        executarProximoDaFila();
    }

    private void encerrarRegistro() {
        if (registroAtual == null || emExecucao() || !filaComandos.isEmpty()) {
            return;
        }

        String nomeDigitado;
        String nomeArquivo;
        while (true) {
            nomeDigitado = JOptionPane.showInputDialog(this, "Nome do teste:",
                    "Encerrar registro", JOptionPane.QUESTION_MESSAGE);

            if (nomeDigitado == null) {
                Object[] opcoes = {"Continuar gravando", "Descartar registro"};
                int escolha = JOptionPane.showOptionDialog(this,
                        "O registro ainda não foi salvo. O que deseja fazer?", "Encerrar registro",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opcoes, opcoes[0]);
                if (escolha == 1) {
                    finalizarRegistro();
                }
                return;
            }

            nomeDigitado = nomeDigitado.trim();
            nomeArquivo = RepositorioHistorico.limparNome(nomeDigitado);
            if (!nomeArquivo.isEmpty()) {
                break;
            }
            JOptionPane.showMessageDialog(this, "Informe um nome válido.");
        }

        Path caminho = repositorioHistorico.caminhoPara(nomeArquivo, registroAtual.getInicio());
        if (Files.exists(caminho)) {
            int resposta = JOptionPane.showConfirmDialog(this,
                    "Já existe o arquivo:\n" + caminho.getFileName() + "\n\nDeseja sobrescrever?\n"
                            + "(\"Não\" salva com outro número no nome)",
                    "Arquivo existente", JOptionPane.YES_NO_CANCEL_OPTION);
            if (resposta == JOptionPane.NO_OPTION) {
                caminho = repositorioHistorico.proximoLivre(caminho);
            } else if (resposta != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            repositorioHistorico.salvar(caminho, registroAtual, nomeDigitado, LocalDateTime.now());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o registro:\n" + ex.getMessage() + "\n\nO registro continua ativo.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this, "Registro salvo em:\n" + caminho);
        finalizarRegistro();
    }

    private void finalizarRegistro() {
        registroAtual = null;
        labelGravando.setVisible(false);
        labelPnSn.setVisible(false);
        atualizarBotoesExecucao();
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo) + "...";
    }

    private void salvarComoTeste() {
        String texto = campoLivre.getText().trim();
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Digite um comando no campo livre antes de salvar.");
            return;
        }

        if (!arquivoCarregado) {
            JOptionPane.showMessageDialog(this,
                    "O arquivo de testes não foi lido corretamente ao abrir o programa.\n"
                            + "Para não apagar os testes existentes, nada será salvo.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Plataforma plataforma = (Plataforma) boxPlataforma.getSelectedItem();
        boolean ehAt = caixaAt.isSelected();

        JTextField campoNome = new JTextField(25);
        JTextField campoDescricao = new JTextField(25);
        JPanel painel = new JPanel(new GridLayout(0, 1, 0, 4));
        painel.add(new JLabel("Nome (obrigatório):"));
        painel.add(campoNome);
        painel.add(new JLabel("Descrição (opcional):"));
        painel.add(campoDescricao);

        String nome;
        while (true) {
            int opcao = JOptionPane.showConfirmDialog(this, painel,
                    "Salvar como teste (" + plataforma + (ehAt ? ", AT" : "") + ")",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                return;
            }
            nome = campoNome.getText().trim();
            if (!nome.isEmpty()) {
                break;
            }
            JOptionPane.showMessageDialog(this, "O nome é obrigatório.");
        }

        Comando novo = new Comando(nome, plataforma, texto, campoDescricao.getText().trim());
        if (ehAt) {
            novo.setTipo(TipoComando.AT);
        }

        List<Comando> novaLista = new ArrayList<>(todosComandos);

        int indiceExistente = -1;
        for (int i = 0; i < novaLista.size(); i++) {
            Comando c = novaLista.get(i);
            if (c.getPlataforma() == plataforma && c.getNome().equals(nome)) {
                indiceExistente = i;
                break;
            }
        }

        if (indiceExistente >= 0) {
            int resposta = JOptionPane.showConfirmDialog(this,
                    "Já existe um teste \"" + nome + "\" em " + plataforma + ".\nDeseja substituir?",
                    "Teste existente", JOptionPane.YES_NO_OPTION);
            if (resposta != JOptionPane.YES_OPTION) {
                return;
            }
            novaLista.set(indiceExistente, novo);
        } else {
            novaLista.add(novo);
        }

        try {
            repositorio.salvar(novaLista);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o arquivo de testes:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        todosComandos = novaLista;
        atualizarLista();
        listaComandos.setSelectedValue(novo, true);
    }

    private void editarTeste() {
        Comando selecionado = listaComandos.getSelectedValue();
        if (selecionado == null) {
            JOptionPane.showMessageDialog(this, "Selecione um teste na lista para editar.");
            return;
        }

        if (!arquivoCarregado) {
            JOptionPane.showMessageDialog(this,
                    "O arquivo de testes não foi lido corretamente ao abrir o programa.\n"
                            + "Para não apagar os testes existentes, nada será alterado.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JTextField campoNome = new JTextField(selecionado.getNome(), 30);
        JTextField campoTexto = new JTextField(selecionado.getTxtComando(), 30);
        campoTexto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JTextField campoDescricao = new JTextField(selecionado.getDescricao(), 30);

        JPanel painel = new JPanel(new GridLayout(0, 1, 0, 4));
        painel.add(new JLabel("Nome (obrigatório):"));
        painel.add(campoNome);
        painel.add(new JLabel("Comando (obrigatório):"));
        painel.add(campoTexto);
        painel.add(new JLabel("Descrição (opcional):"));
        painel.add(campoDescricao);

        Plataforma plataforma = selecionado.getPlataforma();
        String nome;
        String texto;

        while (true) {
            int opcao = JOptionPane.showConfirmDialog(this, painel,
                    "Editar teste (" + plataforma + ")",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                return;
            }

            nome = campoNome.getText().trim();
            texto = campoTexto.getText().trim();

            if (nome.isEmpty() || texto.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nome e comando são obrigatórios.");
                continue;
            }

            if (existeOutroComNome(nome, plataforma, selecionado)) {
                JOptionPane.showMessageDialog(this,
                        "Já existe outro teste \"" + nome + "\" em " + plataforma + ".");
                continue;
            }
            break;
        }

        Comando editado = new Comando(nome, plataforma, texto, campoDescricao.getText().trim());
        editado.setRegras(selecionado.getRegras());
        editado.setInformativo(selecionado.isInformativo());
        editado.setTipo(selecionado.getTipo());
        editado.setEsperaSegundos(selecionado.getEsperaSegundos());

        List<Comando> novaLista = new ArrayList<>(todosComandos);
        int indice = novaLista.indexOf(selecionado);
        novaLista.set(indice, editado);

        try {
            repositorio.salvar(novaLista);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o arquivo de testes:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        todosComandos = novaLista;
        if (ultimoTeste == selecionado) {
            ultimoTeste = editado;
        }
        atualizarLista();
        listaComandos.setSelectedValue(editado, true);
    }

    private boolean existeOutroComNome(String nome, Plataforma plataforma, Comando ignorar) {
        for (Comando c : todosComandos) {
            if (c != ignorar && c.getPlataforma() == plataforma && c.getNome().equals(nome)) {
                return true;
            }
        }
        return false;
    }

    private void excluirTeste() {
        Comando selecionado = listaComandos.getSelectedValue();
        if (selecionado == null) {
            JOptionPane.showMessageDialog(this, "Selecione um teste na lista para excluir.");
            return;
        }

        if (!arquivoCarregado) {
            JOptionPane.showMessageDialog(this,
                    "O arquivo de testes não foi lido corretamente ao abrir o programa.\n"
                            + "Para não apagar os testes existentes, nada será alterado.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int resposta = JOptionPane.showConfirmDialog(this,
                "Excluir o teste \"" + selecionado.getNome() + "\" (" + selecionado.getPlataforma() + ")?\n"
                        + "Esta ação não pode ser desfeita.",
                "Excluir teste", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resposta != JOptionPane.YES_OPTION) {
            return;
        }

        int posicaoNaLista = listaComandos.getSelectedIndex();

        List<Comando> novaLista = new ArrayList<>(todosComandos);
        novaLista.remove(selecionado);

        try {
            repositorio.salvar(novaLista);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o arquivo de testes:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        todosComandos = novaLista;
        atualizarLista();

        if (!modeloLista.isEmpty()) {
            int novaPosicao = Math.min(posicaoNaLista, modeloLista.size() - 1);
            listaComandos.setSelectedIndex(novaPosicao);
        }
    }

    private void alternarConexao() {
        if (conexao.estaConectado()) {
            conexao.desconectar();
            atualizarStatus(false, "Desconectado");
            // Sem Telnet, só os itens do Prolin saem da fila
            filaComandos.removeIf(c -> c.plataforma == Plataforma.PROLIN);
            finalizarExecucao(StatusExecucao.CONEXAO_PERDIDA);
            atualizarBotoesExecucao();
            return;
        }

        String host = campoHost.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe o IP do terminal.");
            return;
        }

        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Porta inválida.");
            return;
        }

        botaoConectar.setEnabled(false);
        labelStatus.setText("Conectando...");
        labelStatus.setForeground(Color.ORANGE);

        loginEnviado = false;

        // Rede/processo fora da EDT; a tela só é atualizada via invokeLater
        new Thread(() -> {
            try {
                conexao.conectar(host, porta,
                        texto -> SwingUtilities.invokeLater(() -> mostrarRetorno(texto)),
                        () -> SwingUtilities.invokeLater(() -> {
                            atualizarStatus(false, "Desconectado");
                            // Sem Telnet, só os itens do Prolin saem da fila
                            filaComandos.removeIf(c -> c.plataforma == Plataforma.PROLIN);
                            finalizarExecucao(StatusExecucao.CONEXAO_PERDIDA);
                            atualizarBotoesExecucao();
                        }));
                SwingUtilities.invokeLater(() -> {
                    enderecoConectado = host + ":" + porta;
                    atualizarStatus(true, "Conectado a " + enderecoConectado);
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() ->
                        atualizarStatus(false, "Falha: " + ex.getMessage()));
            }
        }).start();
    }

    private void mostrarRetorno(String texto) {
        if (coletor.emAndamento()) {
            ResultadoExecucao resultado = coletor.receber(texto, this::exibirTexto);
            if (resultado != null) {
                encerrarExecucao(resultado);
                exibirTexto(coletor.retirarSobra());
            }
        } else {
            exibirTexto(texto);
        }

        if (!loginEnviado && texto.contains("login:")) {
            loginEnviado = true;
            try {
                conexao.enviar(campoLogin.getText());
            } catch (IOException ex) {
                areaRetorno.append("\n[Erro ao enviar login: " + ex.getMessage() + "]\n");
            }
        }
    }

    private void exibirTexto(String texto) {
        if (texto.isEmpty()) {
            return;
        }
        areaRetorno.append(texto);
        areaRetorno.setCaretPosition(areaRetorno.getDocument().getLength());
    }

    private void garantirQuebraDeLinha() {
        String atual = areaRetorno.getText();
        if (!atual.isEmpty() && !atual.endsWith("\n")) {
            exibirTexto("\n");
        }
    }

    private void atualizarStatus(boolean conectado, String texto) {
        labelStatus.setText(texto);
        labelStatus.setForeground(conectado ? new Color(0, 128, 0) : Color.RED);
        botaoConectar.setText(conectado ? "Desconectar" : "Conectar");
        botaoConectar.setEnabled(true);
    }
}
