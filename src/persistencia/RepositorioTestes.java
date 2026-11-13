package persistencia;

import avaliacao.Regra;
import avaliacao.Veredito;
import model.Comando;
import model.Plataforma;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * Responsável por ler e gravar os testes no arquivo <pasta do usuário>/BashTool/testes.txt.
 * A tela não sabe nada sobre o formato do arquivo: ela só chama carregar() e salvar().
 */
public class RepositorioTestes {

    private static final String INICIO_BLOCO = "[teste]";

    private final Path pasta;
    private final Path arquivo;

    public RepositorioTestes() {
        // user.home é a pasta do usuário (ex.: C:\Users\fulano)
        pasta = Paths.get(System.getProperty("user.home"), "BashTool");
        arquivo = pasta.resolve("testes.txt");
    }

    public Path getArquivo() {
        return arquivo;
    }

    /*
     * Lê todos os testes do arquivo.
     * Se o arquivo ainda não existir, cria com os testes padrão e devolve esses testes.
     */
    public List<Comando> carregar() throws IOException {
        if (!Files.exists(arquivo)) {
            List<Comando> padrao = testesPadrao();
            salvar(padrao);
            return padrao;
        }

        List<Comando> testes = new ArrayList<>();
        Bloco bloco = null; // bloco [teste] que está sendo lido no momento

        // try-with-resources: o leitor é fechado automaticamente no final
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String linha;
            boolean primeiraLinha = true;

            while ((linha = leitor.readLine()) != null) {
                // O Bloco de Notas pode gravar um caractere invisível (BOM) no início do arquivo
                if (primeiraLinha && linha.startsWith("\uFEFF")) {
                    linha = linha.substring(1);
                }
                primeiraLinha = false;

                String limpa = linha.trim();

                // linhas em branco e comentários são ignorados
                if (limpa.isEmpty() || limpa.startsWith("#")) {
                    continue;
                }

                // começo de um novo bloco: fecha o anterior (se houver) e abre outro
                if (limpa.equals(INICIO_BLOCO)) {
                    adicionarSeValido(bloco, testes);
                    bloco = new Bloco();
                    continue;
                }

                // linha chave=valor fora de um bloco: ignora
                if (bloco == null) {
                    continue;
                }

                // separa SOMENTE no primeiro "=", pois o comando pode conter outros "="
                int posicao = linha.indexOf('=');
                if (posicao < 0) {
                    continue; // linha sem "=": ignora
                }
                String chave = linha.substring(0, posicao).trim();
                String valor = linha.substring(posicao + 1).trim();

                switch (chave) {
                    case "nome":        bloco.nome = valor; break;
                    case "plataforma":  bloco.plataforma = valor; break;
                    case "comando":     bloco.comando = valor; break;
                    case "descricao":   bloco.descricao = valor; break;
                    // regras: podem aparecer várias vezes no mesmo bloco
                    case "ok":          adicionarRegra(bloco, Veredito.OK, valor); break;
                    case "alerta":      adicionarRegra(bloco, Veredito.ALERTA, valor); break;
                    case "falha":       adicionarRegra(bloco, Veredito.FALHA, valor); break;
                    case "informativo": bloco.informativo = valor.equalsIgnoreCase("sim"); break;
                    default:
                        // chave desconhecida: ignorada (permite campos novos no futuro)
                        break;
                }
            }
        }

        // o último bloco do arquivo não tem um "[teste]" depois dele, então fecha aqui
        adicionarSeValido(bloco, testes);

        // Arquivos antigos: completa os testes padrão que ainda não têm regras
        if (completarRegrasPadrao(testes)) {
            try {
                salvar(testes);
            } catch (IOException e) {
                // não impede o uso: as regras já estão na memória, só não foram gravadas
                System.err.println("Não foi possível gravar as regras padrão: " + e.getMessage());
            }
        }
        return testes;
    }

    /*
     * Reescreve o arquivo inteiro com a lista recebida.
     */
    public void salvar(List<Comando> testes) throws IOException {
        Files.createDirectories(pasta); // cria a pasta se não existir

        try (BufferedWriter escritor = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            boolean primeiro = true;
            for (Comando c : testes) {
                if (!primeiro) {
                    escritor.newLine(); // linha em branco entre os blocos
                }
                primeiro = false;

                escritor.write(INICIO_BLOCO);
                escritor.newLine();
                escreverCampo(escritor, "nome", c.getNome());
                escreverCampo(escritor, "plataforma", c.getPlataforma().name()); // PROLIN / ANDROID
                escreverCampo(escritor, "comando", c.getTxtComando());
                escreverCampo(escritor, "descricao", c.getDescricao());

                // campos novos: só são gravados quando existem
                if (c.isInformativo()) {
                    escreverCampo(escritor, "informativo", "sim");
                }
                for (Regra r : c.getRegras()) {
                    // a chave é o tipo em minúsculas: ok, alerta ou falha
                    escreverCampo(escritor, r.getTipo().name().toLowerCase(Locale.ROOT), r.paraTexto());
                }
            }
        }
    }

    private void escreverCampo(BufferedWriter escritor, String chave, String valor) throws IOException {
        if (valor == null) {
            valor = "";
        }
        // cada campo ocupa uma linha só: quebras de linha virariam um campo "quebrado"
        valor = valor.replace("\r", " ").replace("\n", " ");
        escritor.write(chave + "=" + valor);
        escritor.newLine();
    }

    private void adicionarRegra(Bloco bloco, Veredito tipo, String valor) {
        if (!valor.isEmpty()) {
            bloco.regras.add(Regra.deTexto(tipo, valor));
        }
    }

    /*
     * Transforma o bloco lido em um Comando, se ele estiver completo.
     * Blocos inválidos são simplesmente pulados, sem derrubar a leitura do arquivo.
     */
    private void adicionarSeValido(Bloco bloco, List<Comando> testes) {
        if (bloco == null) {
            return;
        }
        if (vazio(bloco.nome) || vazio(bloco.plataforma) || vazio(bloco.comando)) {
            return; // bloco incompleto
        }

        Plataforma plataforma;
        try {
            // valueOf converte o texto "PROLIN" no valor Plataforma.PROLIN
            plataforma = Plataforma.valueOf(bloco.plataforma.toUpperCase());
        } catch (IllegalArgumentException e) {
            return; // plataforma inválida
        }

        String descricao = (bloco.descricao == null) ? "" : bloco.descricao;
        Comando comando = new Comando(bloco.nome, plataforma, bloco.comando, descricao);
        comando.setRegras(bloco.regras);
        comando.setInformativo(bloco.informativo);
        testes.add(comando);
    }

    /*
     * Para cada teste carregado que tem o mesmo nome e plataforma de um teste padrão,
     * mas ainda não tem nenhuma regra (nem é informativo), copia as regras padrão.
     * Devolve true se algo mudou (para o arquivo ser regravado).
     */
    private boolean completarRegrasPadrao(List<Comando> testes) {
        boolean mudou = false;
        List<Comando> padroes = testesPadrao();

        for (Comando t : testes) {
            if (!t.getRegras().isEmpty() || t.isInformativo()) {
                continue; // já tem regras: não mexe
            }
            for (Comando p : padroes) {
                boolean mesmoTeste = p.getNome().equals(t.getNome()) && p.getPlataforma() == t.getPlataforma();
                boolean padraoTemRegras = !p.getRegras().isEmpty() || p.isInformativo();
                if (mesmoTeste && padraoTemRegras) {
                    t.setRegras(p.getRegras());
                    t.setInformativo(p.isInformativo());
                    mudou = true;
                    break;
                }
            }
        }
        return mudou;
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isEmpty();
    }

    /*
     * Testes gravados na primeira vez que o programa roda, já com as regras padrão.
     */
    public List<Comando> testesPadrao() {
        List<Comando> padrao = new ArrayList<>();

        Comando enderecos = new Comando("Endereços IP", Plataforma.PROLIN,
                "ip addr show", "Mostra os IPs de cada interface");
        // IPv6 global começa com 2 ou 3 (2000::/3); fe80:: (link-local) não conta
        enderecos.adicionarRegra(Regra.deTexto(Veredito.OK, "re:inet6 [23][0-9a-f]{3}: => IPv6 global presente"));
        padrao.add(enderecos);

        Comando rotas = new Comando("Rotas IPv6", Plataforma.PROLIN,
                "ip -6 route show", "Verifica se existe rota default IPv6");
        rotas.adicionarRegra(Regra.deTexto(Veredito.OK, "re:(?m)^default => Rota default IPv6 presente"));
        padrao.add(rotas);

        Comando dns = new Comando("Servidores DNS", Plataforma.PROLIN,
                "cat /etc/resolv.conf", "Mostra os DNS recebidos da rede");
        dns.setInformativo(true);
        padrao.add(dns);

        Comando pingV4 = new Comando("Ping IPv4 Google", Plataforma.PROLIN,
                "ping -c 3 8.8.8.8", "Testa conectividade IPv4 sem DNS");
        pingV4.setRegras(regrasPing("Sem rota IPv4", "Sem resposta IPv4", "IPv4 OK"));
        padrao.add(pingV4);

        Comando pingLte = new Comando("Ping IPv6 pelo LTE", Plataforma.PROLIN,
                "ping6 -I seth_lte0 -c 3 2001:4860:4860::8888", "Testa IPv6 pela rede celular");
        pingLte.setRegras(regrasPing("Sem rota IPv6", "Sem resposta IPv6", "IPv6 OK"));
        padrao.add(pingLte);

        Comando nat64 = new Comando("Teste NAT64", Plataforma.PROLIN,
                "ping6 -c 3 64:ff9b::808:808", "Alcança o 8.8.8.8 via NAT64");
        nat64.setRegras(regrasPing("Sem rota IPv6", "NAT64 indisponível", "NAT64 OK"));
        padrao.add(nat64);

        padrao.add(new Comando("Endereços IP", Plataforma.ANDROID,
                "ip addr show", "Mostra IPs e interface v4- do CLAT"));

        Comando pingAndroid = new Comando("Ping IPv6 Google", Plataforma.ANDROID,
                "ping6 -c 3 2001:4860:4860::8888", "Testa conectividade IPv6 sem DNS");
        pingAndroid.setRegras(regrasPing("Sem rota IPv6", "Sem resposta IPv6", "IPv6 OK"));
        padrao.add(pingAndroid);

        return padrao;
    }

    /*
     * Regras comuns dos testes de ping; só os rótulos mudam.
     *
     * Por que não usar só "0% packet loss" para o OK?  Porque esse texto também aparece
     * DENTRO de "100% packet loss" ("10" + "0% packet loss"). Um ping que falhou
     * totalmente seria marcado como OK. Por isso o OK exige que antes do "0" venha
     * o início da linha ou um caractere que NÃO seja dígito: (^|[^0-9])0% packet loss.
     * Pelo mesmo motivo, \b100% e \b[1-9][0-9]?% usam \b (fronteira de palavra), para
     * não pegar um pedaço de outro número.
     */
    private List<Regra> regrasPing(String semRota, String semResposta, String ok) {
        List<Regra> regras = new ArrayList<>();
        regras.add(Regra.deTexto(Veredito.FALHA, "Network is unreachable => " + semRota));
        regras.add(Regra.deTexto(Veredito.FALHA, "re:\\b100% packet loss => " + semResposta));
        regras.add(Regra.deTexto(Veredito.ALERTA, "re:\\b[1-9][0-9]?% packet loss => Perda parcial"));
        regras.add(Regra.deTexto(Veredito.OK, "re:(^|[^0-9])0% packet loss => " + ok));
        return regras;
    }

    /*
     * Classe auxiliar só para guardar os textos de um bloco enquanto ele é lido.
     * É "private static" porque só o repositório usa.
     */
    private static class Bloco {
        String nome;
        String plataforma;
        String comando;
        String descricao;
        List<Regra> regras = new ArrayList<>();
        boolean informativo = false;
    }
}
