package TelaPrincipal;

import avaliacao.AvaliadorResultado;
import avaliacao.ExecucaoAvaliada;
import avaliacao.Regra;
import avaliacao.RegistroTeste;
import avaliacao.ResultadoAvaliado;
import avaliacao.Veredito;
import conexao.ColetorRetorno;
import conexao.ConexaoTelnet;
import model.Comando;
import model.Plataforma;
import model.ResultadoExecucao;
import model.StatusExecucao;
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
    private DefaultListModel<Comando> modeloLista; // guarda os comandos no objeto
    private JList<Comando> listaComandos;
    private List<Comando> todosComandos = new ArrayList<>();

    private JTextField campoComando;
    private JTextArea areaDescricao;

    private JTextArea areaRetorno;
    private JButton botaoEnviar;
    private JButton botaoLimpar;
    private JButton botaoEditar;  // altera nome, comando e descrição do teste selecionado
    private JButton botaoExcluir; // remove o teste selecionado da lista e do arquivo

    private JTextField campoHost;
    private JTextField campoPorta;
    private JButton botaoConectar;
    private JLabel labelStatus;
    private ConexaoTelnet conexao = new ConexaoTelnet();

    private JTextField campoLogin;
    private boolean loginEnviado = false;

    // Comando livre: texto digitado na hora, que pode ser enviado ou salvo como teste
    private JTextField campoLivre;
    private JButton botaoEnviarLivre;
    private JButton botaoSalvarTeste;

    // Persistência dos testes no arquivo testes.txt
    private RepositorioTestes repositorio = new RepositorioTestes();
    // fica false se a leitura do arquivo falhar, para não sobrescrevê-lo com uma lista incompleta
    private boolean arquivoCarregado = false;

    // Tempo máximo de uma execução, em milissegundos. Para mudar, altere só este valor.
    private static final int TEMPO_LIMITE_MS = 30_000;

    // Controle das execuções: uma por vez, cada uma com número sequencial
    private ColetorRetorno coletor = new ColetorRetorno();
    private int contadorExecucao = 0;
    private Timer timerLimite;          // javax.swing.Timer: dispara na EDT
    private JButton botaoCancelar;
    private ResultadoExecucao ultimoResultado;

    // Dados da execução em andamento (usados quando ela termina)
    private Comando execucaoAtualTeste;           // null = comando livre ou PN/SN
    private String execucaoAtualNome;             // nome exibido na tabela
    private String execucaoAtualIdentificacao;    // "PN", "SN" ou null
    private Comando ultimoTeste;                  // teste da última execução (para "Criar regra")

    // Avaliação e painel de resultados
    private AvaliadorResultado avaliador = new AvaliadorResultado();
    private JButton botaoCriarRegra;
    private DefaultTableModel modeloResultados;
    private JTable tabelaResultados;
    private List<ExecucaoAvaliada> linhasResultados = new ArrayList<>(); // mesma ordem das linhas da tabela
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Registro de teste (histórico)
    private RepositorioHistorico repositorioHistorico = new RepositorioHistorico();
    private RegistroTeste registroAtual;          // null = não está gravando
    private JButton botaoRegistrar;
    private JButton botaoEncerrar;
    private JButton botaoLimparResultados;
    private JLabel labelGravando;
    private JLabel labelPnSn;

    // Fila de comandos que esperam a vez de executar (uma execução por vez)
    private Deque<ComandoPendente> filaComandos = new ArrayDeque<>();

    /* Um item da fila: o texto a enviar e se é a leitura do PN ou do SN */
    private static class ComandoPendente {
        final String texto;
        final String identificacao;

        ComandoPendente(String texto, String identificacao) {
            this.texto = texto;
            this.identificacao = identificacao;
        }
    }

    public TelaPrincipal(){
        setTitle("ShellTool");
        setSize(1280, 720); // maior, para caber o painel de resultados
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // topo: label + setinha
        JPanel painelTopo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelTopo.add(new JLabel("Plataforma"));
        boxPlataforma = new JComboBox<>(Plataforma.values());
        painelTopo.add(boxPlataforma);

        painelTopo.add(Box.createHorizontalStrut(20));
        painelTopo.add(new JLabel("IP:"));
        campoHost = new JTextField(15);
        painelTopo.add(campoHost);

        painelTopo.add(new JLabel("Porta:"));
        campoPorta = new JTextField("2323", 5);
        painelTopo.add(campoPorta);

        painelTopo.add(new JLabel("Login:"));
        campoLogin = new JTextField(10);
        painelTopo.add(campoLogin);

        botaoConectar = new JButton("Conectar");
        painelTopo.add(botaoConectar);

        labelStatus = new JLabel("Desconectado");
        labelStatus.setForeground(Color.RED);
        painelTopo.add(labelStatus);

        add(painelTopo, BorderLayout.NORTH);

        // Esquerda: lista de comandos
        modeloLista = new DefaultListModel<>();
        listaComandos = new JList<>(modeloLista);
        JScrollPane scrollLista = new JScrollPane(listaComandos);
        scrollLista.setPreferredSize(new Dimension(180, 0));
        add(scrollLista, BorderLayout.WEST);

        // Centro: detalhes do comando selecionado
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

        // Botões
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        botaoEnviar = new JButton("Enviar");
        botaoLimpar = new JButton("Limpar retorno");
        painelBotoes.add(botaoEnviar);
        painelBotoes.add(Box.createHorizontalStrut(10));
        // Cancelar: só fica habilitado enquanto há uma execução em andamento
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

        // Área de retorno (esquerda): rótulo + botão "Criar regra da seleção" + saída crua
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

        // Painel de resultados (direita)
        JPanel painelResultados = criarPainelResultados();

        // Os dois lados com o mesmo tamanho preferido: a divisória começa no meio
        painelRetorno.setPreferredSize(new Dimension(400, 300));
        painelResultados.setPreferredSize(new Dimension(400, 300));
        painelRetorno.setMinimumSize(new Dimension(150, 100));
        painelResultados.setMinimumSize(new Dimension(150, 100));

        // JSplitPane: dois painéis lado a lado com uma divisória que o usuário arrasta
        JSplitPane divisao = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, painelRetorno, painelResultados);
        divisao.setResizeWeight(0.5);      // ao aumentar a janela, os dois lados crescem por igual
        divisao.setContinuousLayout(true); // redesenha enquanto arrasta
        divisao.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelDetalhes.add(divisao);
        // garante a divisória no meio depois que a janela já tem tamanho na tela
        SwingUtilities.invokeLater(() -> divisao.setDividerLocation(0.5));


        // Parte inferior da janela: comando livre numa linha só, como a linha de um terminal
        // [Comando livre:] [ campo que estica .................. ] [Enviar livre] [Salvar como teste]
        JPanel painelInferior = new JPanel(new BorderLayout(8, 0)); // 8 px entre as regiões
        painelInferior.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        painelInferior.add(new JLabel("Comando livre:"), BorderLayout.WEST);

        campoLivre = new JTextField();
        campoLivre.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        painelInferior.add(campoLivre, BorderLayout.CENTER); // CENTER ocupa todo o espaço que sobra

        JPanel painelBotoesLivre = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        botaoEnviarLivre = new JButton("Enviar livre");
        botaoSalvarTeste = new JButton("Salvar como teste");
        painelBotoesLivre.add(botaoEnviarLivre);
        painelBotoesLivre.add(Box.createHorizontalStrut(10));
        painelBotoesLivre.add(botaoSalvarTeste);
        painelInferior.add(painelBotoesLivre, BorderLayout.EAST);

        // Painel central = detalhes em cima + comando livre embaixo.
        // Fica no CENTER da janela, então a lista de testes (WEST) continua do topo até o fim.
        JPanel painelCentral = new JPanel(new BorderLayout());
        painelCentral.add(painelDetalhes, BorderLayout.CENTER);
        painelCentral.add(painelInferior, BorderLayout.SOUTH);
        add(painelCentral, BorderLayout.CENTER);

        carregarTestes();
        atualizarLista();

        boxPlataforma.addActionListener(e -> atualizarLista());
        listaComandos.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrarDetalhes();
            }
        });
        botaoEnviar.addActionListener(e -> enviarComando());
        botaoLimpar.addActionListener(e -> areaRetorno.setText(""));
        botaoConectar.addActionListener(e -> alternarConexao());

        // No JTextField, apertar Enter dispara o ActionListener, igual a clicar no botão
        botaoEnviarLivre.addActionListener(e -> enviarComandoLivre());
        campoLivre.addActionListener(e -> enviarComandoLivre());
        botaoSalvarTeste.addActionListener(e -> salvarComoTeste());

        // Exclusão: pelo botão ou pela tecla Delete com a lista em foco
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

        // Timer de uma só vez (sem repetir): se disparar, a execução estourou o tempo
        timerLimite = new Timer(TEMPO_LIMITE_MS, e -> finalizarExecucao(StatusExecucao.TEMPO_ESGOTADO));
        timerLimite.setRepeats(false);

        // Regras, resultados e registro
        botaoCriarRegra.addActionListener(e -> criarRegraDaSelecao());
        botaoLimparResultados.addActionListener(e -> limparResultados());
        botaoRegistrar.addActionListener(e -> registrarTeste());
        botaoEncerrar.addActionListener(e -> encerrarRegistro());

        // Duplo clique numa linha da tabela abre os detalhes da execução
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
    }

    /*
     * Monta o painel "Resultados": botões de registro no topo, linha de PN/SN
     * (só durante o registro) e a tabela com todas as execuções da sessão.
     */
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
        labelGravando.setVisible(false); // só aparece durante o registro

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

        // Modelo da tabela: guarda os dados; isCellEditable=false impede edição com duplo clique
        modeloResultados = new DefaultTableModel(new Object[]{"Hora", "Teste", "Veredito", "Detalhe"}, 0) {
            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
        tabelaResultados = new JTable(modeloResultados);
        tabelaResultados.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabelaResultados.setRowHeight(22);
        // a coluna 2 (Veredito) usa o nosso "pintor" colorido
        tabelaResultados.getColumnModel().getColumn(2).setCellRenderer(new RendererVeredito());
        tabelaResultados.getColumnModel().getColumn(0).setPreferredWidth(70);
        tabelaResultados.getColumnModel().getColumn(1).setPreferredWidth(150);
        tabelaResultados.getColumnModel().getColumn(2).setPreferredWidth(80);
        tabelaResultados.getColumnModel().getColumn(3).setPreferredWidth(250);

        painel.add(new JScrollPane(tabelaResultados), BorderLayout.CENTER);
        return painel;
    }

    // Lê os testes do arquivo; em caso de erro, avisa e segue com a lista vazia
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

        campoComando.setText(selecionado.getTxtComando());
        areaDescricao.setText(selecionado.getDescricao());
    }
    private void enviarComando() {
        if (coletor.emAndamento()) {
            return; // só uma execução por vez
        }

        Comando selecionado = listaComandos.getSelectedValue();

        if (selecionado == null) {
            JOptionPane.showMessageDialog(this, "Selecione um comando na lista.");
            return;
        }

        if (selecionado.getPlataforma() != Plataforma.PROLIN) {
            JOptionPane.showMessageDialog(this, "Envio para Android ainda não implementado.");
            return;
        }

        if (!conexao.estaConectado()) {
            JOptionPane.showMessageDialog(this, "Conecte-se ao terminal primeiro.");
            return;
        }

        executar(selecionado.getTxtComando(), selecionado, selecionado.getNome(), null);
    }

    // Envia o texto digitado no campo "Comando livre", com as mesmas verificações do enviarComando
    private void enviarComandoLivre() {
        // também bloqueia a tecla Enter do campo enquanto há execução
        if (coletor.emAndamento()) {
            return;
        }

        String texto = campoLivre.getText().trim();

        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Digite um comando no campo livre.");
            return;
        }

        // aqui não há comando selecionado, então vale a plataforma da setinha
        if (boxPlataforma.getSelectedItem() != Plataforma.PROLIN) {
            JOptionPane.showMessageDialog(this, "Envio para Android ainda não implementado.");
            return;
        }

        if (!conexao.estaConectado()) {
            JOptionPane.showMessageDialog(this, "Conecte-se ao terminal primeiro.");
            return;
        }

        // comando livre não é um teste salvo: Comando = null
        if (executar(texto, null, "Livre: " + cortar(texto, 40), null)) {
            campoLivre.setText(""); // limpa o campo depois de enviar
        }
    }

    /*
     * Envia um comando com o marcador de fim e começa a acompanhar a execução.
     * - teste: o teste salvo (null para comando livre e PN/SN), usado na avaliação
     * - nomeExibicao: o que aparece na coluna "Teste" da tabela
     * - identificacao: "PN" ou "SN" quando é a leitura de identificação do registro
     * Devolve true se o envio deu certo.
     */
    private boolean executar(String textoComando, Comando teste, String nomeExibicao, String identificacao) {
        int numero = ++contadorExecucao; // 1, 2, 3...
        String linha = ColetorRetorno.montarLinhaComMarcador(textoComando, numero);

        garantirQuebraDeLinha();
        exibirTexto("> " + textoComando + "\n"); // cabeçalho, sem o marcador

        try {
            conexao.enviar(linha);
        } catch (IOException ex) {
            exibirTexto("[Erro ao enviar: " + ex.getMessage() + "]\n");
            return false;
        }

        // Pode iniciar o coletor DEPOIS de enviar: a resposta só é processada em
        // mostrarRetorno, que roda na EDT, e a EDT está ocupada com este método agora.
        coletor.iniciar(numero, textoComando);
        execucaoAtualTeste = teste;
        execucaoAtualNome = nomeExibicao;
        execucaoAtualIdentificacao = identificacao;
        timerLimite.restart();
        atualizarBotoesExecucao();
        return true;
    }

    // Botão "Cancelar": manda Ctrl+C. No ash isso interrompe a linha inteira, então o marcador não virá.
    private void cancelarExecucao() {
        if (!coletor.emAndamento()) {
            return;
        }
        try {
            conexao.enviarBytes(new byte[]{0x03}); // 0x03 = Ctrl+C
        } catch (IOException ex) {
            exibirTexto("\n[Erro ao enviar Ctrl+C: " + ex.getMessage() + "]\n");
        }
        finalizarExecucao(StatusExecucao.CANCELADO);
    }

    // Encerra a execução sem o marcador (tempo esgotado, cancelado ou conexão perdida)
    private void finalizarExecucao(StatusExecucao status) {
        ResultadoExecucao resultado = coletor.finalizar(status, this::exibirTexto);
        if (resultado != null) { // null = não havia execução em andamento
            encerrarExecucao(resultado);
        }
    }

    // Chamado sempre que uma execução termina, por qualquer motivo
    private void encerrarExecucao(ResultadoExecucao resultado) {
        timerLimite.stop();
        ultimoResultado = resultado;
        ultimoTeste = execucaoAtualTeste;
        aoConcluirExecucao(resultado);
        atualizarBotoesExecucao();

        // Se há comandos esperando na fila, dispara o próximo.
        // invokeLater: deixa a EDT terminar de exibir o texto atual (ex.: o prompt) antes.
        if (!filaComandos.isEmpty()) {
            SwingUtilities.invokeLater(this::executarProximoDaFila);
        }
    }

    /*
     * FILA DE COMANDOS: só existe uma execução por vez, então comandos automáticos
     * (como PN e SN) esperam numa fila. Cada vez que uma execução termina,
     * encerrarExecucao() chama este método, que tira o próximo da fila e envia.
     */
    private void executarProximoDaFila() {
        if (coletor.emAndamento() || filaComandos.isEmpty()) {
            return; // ainda ocupado (ele será chamado de novo ao terminar) ou nada a fazer
        }
        if (!conexao.estaConectado()) {
            filaComandos.clear();
            atualizarBotoesExecucao();
            return;
        }
        ComandoPendente proximo = filaComandos.poll(); // poll = tira o primeiro da fila
        if (!executar(proximo.texto, null, proximo.identificacao, proximo.identificacao)) {
            filaComandos.clear(); // o envio falhou: não adianta tentar os outros
            atualizarBotoesExecucao();
        }
    }

    /*
     * Chamado ao fim de cada execução: escreve o rodapé, avalia o resultado,
     * coloca na tabela e, se estiver gravando, no registro.
     */
    private void aoConcluirExecucao(ResultadoExecucao resultado) {
        String rodape;
        switch (resultado.getStatus()) {
            case CONCLUIDO:
                // vírgula decimal: "2,1 s"
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
            // leitura de PN/SN: o veredito é sempre INFO e o detalhe é o próprio valor
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
            avaliacao = avaliador.avaliar(execucaoAtualTeste, resultado);
        }

        ExecucaoAvaliada execucao = new ExecucaoAvaliada(LocalTime.now().withNano(0),
                execucaoAtualNome, resultado, avaliacao);
        adicionarNaTabela(execucao);
        if (registroAtual != null) {
            registroAtual.adicionar(execucao);
        }
    }

    /* Valor do getprop: saída sem espaços nas pontas; vazio ou com erro vira "não identificado" */
    private String extrairIdentificacao(ResultadoExecucao resultado) {
        Integer codigo = resultado.getCodigoSaida();
        if (resultado.getStatus() != StatusExecucao.CONCLUIDO || codigo == null || codigo != 0) {
            return RegistroTeste.NAO_IDENTIFICADO;
        }
        String valor = resultado.getSaida().trim();
        int quebra = valor.indexOf('\n');
        if (quebra >= 0) {
            valor = valor.substring(0, quebra).trim(); // usa só a primeira linha
        }
        return valor.isEmpty() ? RegistroTeste.NAO_IDENTIFICADO : valor;
    }

    /*
     * Liga/desliga botões conforme o estado atual:
     * - durante uma execução: Enviar/Enviar livre desligados, Cancelar ligado
     * - Registrar: só quando não está gravando
     * - Encerrar: só gravando, sem execução em andamento e sem fila pendente
     */
    private void atualizarBotoesExecucao() {
        boolean emExecucao = coletor.emAndamento();
        boolean gravando = registroAtual != null;

        botaoEnviar.setEnabled(!emExecucao);
        botaoEnviarLivre.setEnabled(!emExecucao);
        botaoCancelar.setEnabled(emExecucao);
        botaoRegistrar.setEnabled(!gravando);
        botaoEncerrar.setEnabled(gravando && !emExecucao && filaComandos.isEmpty());
        atualizarLabelPnSn();
    }

    // Mostra "PN: ... | SN: ..." durante o registro
    private void atualizarLabelPnSn() {
        if (registroAtual == null) {
            return;
        }
        boolean aguardando = coletor.emAndamento() || !filaComandos.isEmpty();
        labelPnSn.setText("PN: " + textoIdentificacao(registroAtual.getPn(), aguardando)
                + "  |  SN: " + textoIdentificacao(registroAtual.getSn(), aguardando));
    }

    private String textoIdentificacao(String valor, boolean aguardando) {
        if (valor != null) {
            return valor;
        }
        return aguardando ? "aguardando..." : RegistroTeste.NAO_IDENTIFICADO;
    }

    // ===================== Tabela de resultados =====================

    /* Acrescenta uma linha no fim da tabela e rola até ela */
    private void adicionarNaTabela(ExecucaoAvaliada execucao) {
        linhasResultados.add(execucao);
        modeloResultados.addRow(new Object[]{
                execucao.getHora().format(FORMATO_HORA),
                execucao.getNomeTeste(),
                execucao.getAvaliacao().getVeredito(), // o objeto Veredito: o renderer usa a cor dele
                execucao.getAvaliacao().getDetalhe()
        });
        int ultima = modeloResultados.getRowCount() - 1;
        tabelaResultados.scrollRectToVisible(tabelaResultados.getCellRect(ultima, 0, true));
    }

    /* Limpa só a tabela; um registro em andamento continua com as execuções dele */
    private void limparResultados() {
        modeloResultados.setRowCount(0);
        linhasResultados.clear();
    }

    /* Diálogo do duplo clique: todos os dados da execução e a saída completa */
    private void mostrarDetalhesExecucao(ExecucaoAvaliada execucao) {
        ResultadoExecucao r = execucao.getResultado();
        ResultadoAvaliado a = execucao.getAvaliacao();
        Integer codigo = r.getCodigoSaida();

        JPanel info = new JPanel(new GridLayout(0, 1, 0, 2));
        info.add(new JLabel("Teste: " + execucao.getNomeTeste()
                + "   (" + execucao.getHora().format(FORMATO_HORA) + ")"));

        JTextField campoTexto = new JTextField(r.getTextoComando());
        campoTexto.setEditable(false); // permite selecionar e copiar, mas não editar
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
        areaSaida.setCaretPosition(0); // começa mostrando o topo da saída

        JPanel painel = new JPanel(new BorderLayout(0, 8));
        painel.add(info, BorderLayout.NORTH);
        painel.add(new JScrollPane(areaSaida), BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, painel, "Detalhes da execução", JOptionPane.PLAIN_MESSAGE);
    }

    // ===================== Regras =====================

    /*
     * Cria uma regra a partir do trecho selecionado na área de retorno
     * e adiciona ao teste da última execução.
     */
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

        // Diálogo: tipo, padrão (já com a seleção), rótulo e caixa "expressão regular"
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
                // " => " separa padrão e rótulo no arquivo; no padrão ele confundiria a leitura
                JOptionPane.showMessageDialog(this, "O padrão não pode conter \" => \".");
                continue;
            }
            if (caixaRegex.isSelected()) {
                try {
                    Pattern.compile(padrao); // só para validar antes de salvar
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
            ultimoTeste.getRegras().remove(regra); // desfaz na memória
            JOptionPane.showMessageDialog(this,
                    "Erro ao salvar o arquivo de testes:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Regra adicionada ao teste \"" + ultimoTeste.getNome() + "\":\n" + regra);
    }

    /* O arquivo guarda uma linha por campo: da seleção, usa a primeira linha com texto */
    private String primeiraLinha(String texto) {
        for (String linha : texto.split("\n")) {
            if (!linha.isBlank()) {
                return linha.trim();
            }
        }
        return texto.trim();
    }

    // ===================== Registro de teste =====================

    private void registrarTeste() {
        if (registroAtual != null) {
            return;
        }
        if (!conexao.estaConectado()) {
            JOptionPane.showMessageDialog(this, "Conecte-se ao terminal primeiro.");
            return;
        }
        if (boxPlataforma.getSelectedItem() != Plataforma.PROLIN) {
            JOptionPane.showMessageDialog(this, "O registro de teste só está disponível para Prolin.");
            return;
        }
        if (coletor.emAndamento()) {
            JOptionPane.showMessageDialog(this, "Aguarde a execução atual terminar.");
            return;
        }

        registroAtual = new RegistroTeste(LocalDateTime.now().withNano(0));
        labelGravando.setVisible(true);
        labelPnSn.setVisible(true);

        // PN e SN entram na fila e rodam um depois do outro
        filaComandos.add(new ComandoPendente("/startup/bin/getprop ro.fac.pn", "PN"));
        filaComandos.add(new ComandoPendente("/startup/bin/getprop ro.fac.sn", "SN"));
        atualizarBotoesExecucao();
        executarProximoDaFila();
    }

    private void encerrarRegistro() {
        if (registroAtual == null || coletor.emAndamento() || !filaComandos.isEmpty()) {
            return;
        }

        String nomeDigitado;
        String nomeArquivo;
        while (true) {
            nomeDigitado = JOptionPane.showInputDialog(this, "Nome do teste:",
                    "Encerrar registro", JOptionPane.QUESTION_MESSAGE);

            if (nomeDigitado == null) { // cancelou
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
                return; // cancelou: continua gravando
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

    /* Para de gravar; a tabela continua com as linhas */
    private void finalizarRegistro() {
        registroAtual = null;
        labelGravando.setVisible(false);
        labelPnSn.setVisible(false);
        atualizarBotoesExecucao();
    }

    /* Corta textos longos para caber na tabela */
    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo) + "...";
    }

    // Pede nome e descrição e grava o comando livre como um novo teste no arquivo
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

        // Painel com dois campos, mostrado dentro do JOptionPane
        JTextField campoNome = new JTextField(25);
        JTextField campoDescricao = new JTextField(25);
        JPanel painel = new JPanel(new GridLayout(0, 1, 0, 4)); // 1 coluna, quantas linhas precisar
        painel.add(new JLabel("Nome (obrigatório):"));
        painel.add(campoNome);
        painel.add(new JLabel("Descrição (opcional):"));
        painel.add(campoDescricao);

        String nome;
        // repete o diálogo até o usuário informar um nome ou cancelar
        while (true) {
            int opcao = JOptionPane.showConfirmDialog(this, painel,
                    "Salvar como teste (" + plataforma + ")",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                return; // cancelou
            }
            nome = campoNome.getText().trim();
            if (!nome.isEmpty()) {
                break;
            }
            JOptionPane.showMessageDialog(this, "O nome é obrigatório.");
        }

        Comando novo = new Comando(nome, plataforma, texto, campoDescricao.getText().trim());

        // trabalha numa cópia: se a gravação falhar, a lista em memória não muda
        List<Comando> novaLista = new ArrayList<>(todosComandos);

        // procura um teste com o mesmo nome na mesma plataforma
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
            novaLista.set(indiceExistente, novo); // substitui mantendo a posição
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
        listaComandos.setSelectedValue(novo, true); // seleciona e rola até o novo teste
    }

    // Abre um diálogo já preenchido com o teste selecionado e grava as alterações
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

        // Campos já preenchidos com os valores atuais
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

        Plataforma plataforma = selecionado.getPlataforma(); // a plataforma não muda na edição
        String nome;
        String texto;

        // repete até os campos obrigatórios estarem certos ou o usuário cancelar
        while (true) {
            int opcao = JOptionPane.showConfirmDialog(this, painel,
                    "Editar teste (" + plataforma + ")",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                return; // cancelou
            }

            nome = campoNome.getText().trim();
            texto = campoTexto.getText().trim();

            if (nome.isEmpty() || texto.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nome e comando são obrigatórios.");
                continue;
            }

            // não pode ficar com o mesmo nome de OUTRO teste da mesma plataforma
            if (existeOutroComNome(nome, plataforma, selecionado)) {
                JOptionPane.showMessageDialog(this,
                        "Já existe outro teste \"" + nome + "\" em " + plataforma + ".");
                continue;
            }
            break;
        }

        // Nome/comando/descrição não têm setters: criamos um objeto novo e trocamos o antigo por ele
        Comando editado = new Comando(nome, plataforma, texto, campoDescricao.getText().trim());
        // as regras e o "informativo" passam para o novo objeto
        editado.setRegras(selecionado.getRegras());
        editado.setInformativo(selecionado.isInformativo());

        List<Comando> novaLista = new ArrayList<>(todosComandos);
        int indice = novaLista.indexOf(selecionado);
        novaLista.set(indice, editado); // mantém a posição na lista

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
            ultimoTeste = editado; // "Criar regra da seleção" continua apontando para o teste certo
        }
        atualizarLista();
        listaComandos.setSelectedValue(editado, true); // reseleciona e mostra os novos detalhes
    }

    // true se algum teste diferente de "ignorar" já usa esse nome na plataforma
    private boolean existeOutroComNome(String nome, Plataforma plataforma, Comando ignorar) {
        for (Comando c : todosComandos) {
            if (c != ignorar && c.getPlataforma() == plataforma && c.getNome().equals(nome)) {
                return true;
            }
        }
        return false;
    }

    // Remove o teste selecionado, depois de pedir confirmação, e regrava o arquivo
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

        // guarda a posição para selecionar o item vizinho depois de excluir
        int posicaoNaLista = listaComandos.getSelectedIndex();

        // mesma ideia do salvarComoTeste: altera uma cópia e só troca se a gravação der certo
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

        // seleciona o próximo item (ou o último, se o excluído era o último da lista)
        if (!modeloLista.isEmpty()) {
            int novaPosicao = Math.min(posicaoNaLista, modeloLista.size() - 1);
            listaComandos.setSelectedIndex(novaPosicao);
        }
    }

    private void alternarConexao() {
        if (conexao.estaConectado()) {
            conexao.desconectar();
            atualizarStatus(false, "Desconectado");
            filaComandos.clear(); // sem conexão, a fila não tem como continuar
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

        new Thread(() -> {
            try {
                conexao.conectar(host, porta,
                        texto -> SwingUtilities.invokeLater(() -> mostrarRetorno(texto)),
                        () -> SwingUtilities.invokeLater(() -> {
                            atualizarStatus(false, "Desconectado");
                            // se a conexão caiu no meio de uma execução, encerra ela
                            filaComandos.clear();
                            finalizarExecucao(StatusExecucao.CONEXAO_PERDIDA);
                            atualizarBotoesExecucao();
                        }));
                SwingUtilities.invokeLater(() ->
                        atualizarStatus(true, "Conectado a " + host + ":" + porta));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() ->
                        atualizarStatus(false, "Falha: " + ex.getMessage()));
            }
        }).start();
    }

    // Recebe o texto do terminal (sempre na EDT, via invokeLater)
    private void mostrarRetorno(String texto) {
        if (coletor.emAndamento()) {
            // durante uma execução, o coletor filtra o eco e o marcador antes de exibir
            ResultadoExecucao resultado = coletor.receber(texto, this::exibirTexto);
            if (resultado != null) {
                encerrarExecucao(resultado);          // escreve o rodapé
                exibirTexto(coletor.retirarSobra());  // depois o que veio após o marcador (prompt)
            }
        } else {
            exibirTexto(texto); // fora de execução: mostra tudo, como antes
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

    // Acrescenta texto na área de retorno e rola até o fim
    private void exibirTexto(String texto) {
        if (texto.isEmpty()) {
            return;
        }
        areaRetorno.append(texto);
        areaRetorno.setCaretPosition(areaRetorno.getDocument().getLength());
    }

    // Se a última linha da área não terminou, pula uma linha (ex.: depois do prompt "~ $ ")
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









