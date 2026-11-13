package persistencia;

import avaliacao.Regra;
import avaliacao.Veredito;
import model.Comando;
import model.Plataforma;
import model.TipoComando;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
     * Se já existir, completa com os testes padrão que faltarem (ver completarComPadrao).
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
                    case "informativo": bloco.informativo = valor.equalsIgnoreCase("sim"); break;
                    case "tipo":        bloco.tipo = valor; break;
                    case "espera":      bloco.espera = valor; break;
                    default:
                        // regras (ok, alerta, falha, falha_sem): podem aparecer várias vezes no bloco.
                        // criarRegra devolve null para chave desconhecida, que é ignorada
                        // (permite campos novos no futuro)
                        if (!valor.isEmpty()) {
                            Regra regra = criarRegra(chave, valor);
                            if (regra != null) {
                                bloco.regras.add(regra);
                            }
                        }
                        break;
                }
            }
        }

        // o último bloco do arquivo não tem um "[teste]" depois dele, então fecha aqui
        adicionarSeValido(bloco, testes);

        // Arquivos antigos: acrescenta testes padrão novos e completa regras que faltam
        if (completarComPadrao(testes)) {
            try {
                salvar(testes);
            } catch (IOException e) {
                // não impede o uso: os testes já estão na memória, só não foram gravados
                System.err.println("Não foi possível gravar os testes padrão: " + e.getMessage());
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

                // tipo e espera: só nos testes AT (sem a chave, o tipo é SHELL)
                if (c.isAt()) {
                    escreverCampo(escritor, "tipo", TipoComando.AT.name());
                    escreverCampo(escritor, "espera", String.valueOf(c.getEsperaSegundos()));
                }

                // campos novos: só são gravados quando existem
                if (c.isInformativo()) {
                    escreverCampo(escritor, "informativo", "sim");
                }
                for (Regra r : c.getRegras()) {
                    // a chave vem da própria regra: ok, alerta, falha ou falha_sem
                    escreverCampo(escritor, r.getChave(), r.paraTexto());
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

    /* Converte "chave" + "valor" do arquivo numa Regra; null se a chave não for de regra */
    private static Regra criarRegra(String chave, String valor) {
        switch (chave) {
            case "ok":        return Regra.deTexto(Veredito.OK, valor);
            case "alerta":    return Regra.deTexto(Veredito.ALERTA, valor);
            case "falha":     return Regra.deTexto(Veredito.FALHA, valor);
            case "falha_sem": return Regra.deTextoAusencia(valor);
            default:          return null;
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

        // "tipo=AT" (maiúsculas ou minúsculas); qualquer outro valor ou ausência = SHELL
        if (bloco.tipo != null && bloco.tipo.equalsIgnoreCase("AT")) {
            comando.setTipo(TipoComando.AT);
        }
        if (bloco.espera != null) {
            try {
                comando.setEsperaSegundos(Integer.valueOf(bloco.espera));
            } catch (NumberFormatException e) {
                // número inválido: fica a espera padrão
            }
        }
        testes.add(comando);
    }

    /*
     * Compara os testes lidos do arquivo com os testes padrão (pelo par nome + plataforma):
     *  - padrão que não está no arquivo: é acrescentado no final;
     *  - padrão que está no arquivo SEM nenhuma regra (nem informativo): recebe as regras padrão;
     *  - qualquer outro teste (do usuário, ou padrão que já tem regras) não é alterado.
     * Devolve true se algo mudou (para o arquivo ser regravado).
     */
    private boolean completarComPadrao(List<Comando> testes) {
        boolean mudou = false;

        for (Comando p : testesPadrao()) {
            Comando existente = null;
            for (Comando t : testes) {
                if (p.getNome().equals(t.getNome()) && p.getPlataforma() == t.getPlataforma()) {
                    existente = t;
                    break;
                }
            }

            if (existente == null) {
                testes.add(p); // teste padrão novo: entra no fim da lista
                mudou = true;
                continue;
            }

            boolean existenteSemRegras = existente.getRegras().isEmpty() && !existente.isInformativo();
            boolean padraoTemRegras = !p.getRegras().isEmpty() || p.isInformativo();
            if (existenteSemRegras && padraoTemRegras) {
                existente.setRegras(p.getRegras());
                existente.setInformativo(p.isInformativo());
                mudou = true;
            }
        }
        return mudou;
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isEmpty();
    }

    /*
     * Testes padrão, já com as regras. Ficam no arquivo na primeira vez que o programa roda,
     * e os que faltarem são acrescentados nas próximas (ver completarComPadrao).
     *
     * Cada regra é escrita igual ao testes.txt ("chave=valor"), para ficar fácil de comparar.
     * Atenção: em String Java a barra invertida é escrita dobrada: "\\b" vira \b no arquivo.
     */
    public List<Comando> testesPadrao() {
        List<Comando> p = new ArrayList<>();
        final Plataforma PROLIN = Plataforma.PROLIN;
        final Plataforma ANDROID = Plataforma.ANDROID;

        // ===== PROLIN — Rede =====
        // IPv6 global começa com 2 ou 3 (2000::/3); fe80:: (link-local) não conta
        novo(p, PROLIN, "Endereços IP", "ip addr show", "Mostra os IPs de cada interface",
                "falha_sem=re:inet6 [23][0-9a-f]{3}: => Sem IPv6 global",
                "ok=re:inet6 [23][0-9a-f]{3}: => IPv6 global presente");
        novo(p, PROLIN, "Endereços da interface LTE", "ip addr show seth_lte0", "IPs da interface de dados celular",
                "falha=does not exist => Interface LTE inexistente",
                "falha_sem=re:inet6 [23][0-9a-f]{3}: => LTE sem IPv6 global",
                "ok=re:inet6 [23][0-9a-f]{3}: => IPv6 global no LTE");
        novo(p, PROLIN, "Rotas IPv4", "ip route show", "Verifica se existe rota default IPv4",
                "falha_sem=re:(?m)^default => Sem rota IPv4",
                "ok=re:(?m)^default => Rota default IPv4 presente");
        novo(p, PROLIN, "Rotas IPv6", "ip -6 route show", "Verifica se existe rota default IPv6",
                "falha_sem=re:(?m)^default => Sem rota IPv6",
                "ok=re:(?m)^default => Rota default IPv6 presente");
        novo(p, PROLIN, "Servidores DNS", "cat /etc/resolv.conf", "Mostra os DNS recebidos da rede",
                "falha_sem=re:(?m)^nameserver => Sem servidor DNS",
                "ok=re:(?m)^nameserver [0-9a-fA-F]*: => DNS IPv6 configurado");
        novo(p, PROLIN, "Aceita RA no LTE", "cat /proc/sys/net/ipv6/conf/seth_lte0/accept_ra",
                "0 = ignora Router Advertisement",
                "falha=re:(?m)^0$ => Ignora RA",
                "ok=re:(?m)^[12]$ => Aceita RA");
        novo(p, PROLIN, "Endereços IPv6 do kernel", "cat /proc/net/if_inet6", "Lista IPv6 direto do kernel (hex)",
                "informativo=sim");
        novo(p, PROLIN, "Verificar CLAT", "ip addr show | grep -i -E \"v4-|clat\" || echo \"Sem CLAT\"",
                "Prolin não tem CLAT; o esperado é Sem CLAT",
                "alerta=re:v4- => Interface CLAT encontrada",
                "ok=Sem CLAT => Sem CLAT (esperado no Prolin)");

        // ===== PROLIN — Ping e DNS =====
        novo(p, PROLIN, "Ping IPv4 Google", "ping -c 3 8.8.8.8", "Testa conectividade IPv4 sem DNS",
                regrasPing("IPv4"));
        novo(p, PROLIN, "Ping IPv4 por nome", "ping -c 3 google.com", "Testa IPv4 e resolução de nomes",
                comFalhaDns(regrasPing("IPv4")));
        novo(p, PROLIN, "Ping IPv6 Google", "ping6 -c 3 2001:4860:4860::8888", "Testa conectividade IPv6 sem DNS",
                regrasPing("IPv6"));
        novo(p, PROLIN, "Ping IPv6 pelo LTE", "ping6 -I seth_lte0 -c 3 2001:4860:4860::8888",
                "Testa IPv6 pela rede celular", regrasPing("IPv6"));
        novo(p, PROLIN, "Ping IPv6 pelo WiFi", "ping6 -I wlan0 -c 3 2001:4860:4860::8888",
                "Testa IPv6 pela rede WiFi", regrasPing("IPv6"));
        novo(p, PROLIN, "Ping IPv6 por nome", "ping6 -c 3 google.com", "Testa IPv6 e resolução AAAA",
                comFalhaDns(regrasPing("IPv6")));
        novo(p, PROLIN, "Teste NAT64", "ping6 -c 3 64:ff9b::808:808", "Alcança o 8.8.8.8 via NAT64",
                "falha=Network is unreachable => Sem rota IPv6",
                "falha=re:\\b100% packet loss => NAT64 indisponível",
                "alerta=re:\\b[1-9][0-9]?% packet loss => Perda parcial",
                "ok=re:(^|[^0-9])0% packet loss => NAT64 OK");
        novo(p, PROLIN, "Teste DNS64", "ping6 -c 3 ipv4only.arpa", "Verifica se o DNS sintetiza endereços NAT64",
                "falha=bad address => Sem DNS64",
                "ok=re:64:ff9b:: => DNS64 ativo (prefixo padrão)");

        // ===== PROLIN — Terminal =====
        novo(p, PROLIN, "PN do terminal", "/startup/bin/getprop ro.fac.pn", "Part number do terminal",
                "informativo=sim");
        novo(p, PROLIN, "SN do terminal", "/startup/bin/getprop ro.fac.sn", "Número de série do terminal",
                "informativo=sim");

        // ===== PROLIN — Modem (tipo AT: o comando é só o AT, a linha de shell é montada no envio) =====
        // As regras usam padrões ancorados (ex.: "\+CSQ: ") para casar com a resposta do modem,
        // e não com o eco da linha de shell, que contém o comando dentro do echo -e.
        novoAt(p, "AT - Versão do modem", "AT+CGMR", "Firmware do modem");
        novoAt(p, "AT - Fabricante", "AT+CGMI", "Fabricante do modem");
        novoAt(p, "AT - Modelo", "AT+CGMM", "Modelo do modem");
        novoAt(p, "AT - IMEI", "AT+CGSN", "IMEI do modem");
        novoAt(p, "AT - IMSI", "AT+CIMI", "Identificação do SIM na operadora");
        novoAt(p, "AT - Status do SIM", "AT+CPIN?", "READY = SIM pronto",
                "falha=re:not inserted|SIM failure|NOT INSERTED => Sem SIM",
                "falha=SIM PIN => SIM bloqueado por PIN",
                "falha=SIM PUK => SIM bloqueado por PUK",
                "ok=READY => SIM pronto");
        novoAt(p, "AT - Rádio ligado", "AT+CFUN?", "1 = rádio ligado",
                "falha=re:\\+CFUN: [04]\\b => Rádio desligado ou modo avião",
                "ok=re:\\+CFUN: 1\\b => Rádio ligado");
        novoAt(p, "AT - Sinal", "AT+CSQ", "RSSI 0-31 (99 = desconhecido)",
                "falha=re:\\+CSQ: 99, => Sem sinal",
                "alerta=re:\\+CSQ: [0-9], => Sinal fraco",
                "ok=re:\\+CSQ: (1[0-9]|2[0-9]|3[01]), => Sinal bom");
        novoAt(p, "AT - Operadora e tecnologia", "AT+COPS?", "Último número: 0/3 = 2G, 2/4/5/6 = 3G, 7 = 4G",
                "falha=re:(?m)^\\+COPS: [0-9]$ => Sem operadora",
                "alerta=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",[03]\\b => Rede 2G",
                "alerta=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",[2456]\\b => Rede 3G",
                "ok=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",7\\b => Rede 4G (LTE)");
        novoAt(p, "AT - Registro LTE", "AT+CEREG?", "Registro na rede 4G",
                "falha=re:\\+CEREG: [0-9],3\\b => Registro negado",
                "falha=re:\\+CEREG: [0-9],[04]\\b => Não registrado",
                "alerta=re:\\+CEREG: [0-9],2\\b => Procurando rede",
                "ok=re:\\+CEREG: [0-9],[15]\\b => Registrado no LTE");
        novoAt(p, "AT - Registro 2G/3G", "AT+CREG?", "Registro na rede 2G/3G",
                "falha=re:\\+CREG: [0-9],3\\b => Registro negado",
                "falha=re:\\+CREG: [0-9],[04]\\b => Não registrado",
                "alerta=re:\\+CREG: [0-9],2\\b => Procurando rede",
                "ok=re:\\+CREG: [0-9],[15]\\b => Registrado");
        novoAt(p, "AT - APN configurada", "AT+CGDCONT?", "Tipo de PDP: IP, IPV6 ou IPV4V6",
                "alerta=re:\"IP\", => APN só IPv4",
                "ok=re:\"IPV4V6\" => APN dual-stack",
                "ok=re:\"IPV6\" => APN IPv6-only");
        novoAt(p, "AT - Contexto de dados ativo", "AT+CGACT?", "Verifica se há contexto de dados ativo",
                "falha_sem=re:\\+CGACT: [0-9]+,1\\b => Nenhum contexto ativo",
                "ok=re:\\+CGACT: [0-9]+,1\\b => Contexto de dados ativo");
        novoAt(p, "AT - Endereço do contexto", "AT+CGPADDR", "IP entregue pela operadora em cada contexto",
                "informativo=sim");

        // ===== ANDROID (envio ainda não implementado; os comandos ficam sem "adb shell") =====
        novo(p, ANDROID, "Endereços IP", "ip addr show", "Mostra IPs e interface v4- do CLAT",
                "ok=re:v4- => CLAT ativo",
                "ok=re:inet6 [23][0-9a-f]{3}: => IPv6 global presente");
        novo(p, ANDROID, "Tipo de rede", "getprop gsm.network.type", "LTE, HSPA, EDGE...",
                "informativo=sim");
        novo(p, ANDROID, "Rotas IPv6 (todas as tabelas)", "ip -6 route show table all",
                "Android usa uma tabela por rede",
                "falha_sem=re:(?m)^default => Sem rota IPv6",
                "ok=re:(?m)^default => Rota default IPv6 presente");
        novo(p, ANDROID, "Estado do CLAT", "dumpsys connectivity | grep -i -E \"clat|Nat464|v4-\"",
                "Verifica CLAT e prefixo NAT64",
                "informativo=sim");
        novo(p, ANDROID, "Ping IPv4 Google", "ping -c 3 8.8.8.8", "Em rede IPv6-only funciona via CLAT",
                regrasPing("IPv4"));
        novo(p, ANDROID, "Ping IPv6 Google", "ping6 -c 3 2001:4860:4860::8888", "Testa conectividade IPv6 sem DNS",
                regrasPing("IPv6"));

        return p;
    }

    /* Cria um teste padrão SHELL e aplica as linhas "chave=valor" (regras e informativo) */
    private Comando novo(List<Comando> lista, Plataforma plataforma, String nome, String comando,
                         String descricao, String... linhas) {
        Comando c = new Comando(nome, plataforma, comando, descricao);
        for (String linha : linhas) {
            int posicao = linha.indexOf('='); // mesmo critério do arquivo: o primeiro "="
            String chave = linha.substring(0, posicao);
            String valor = linha.substring(posicao + 1);
            if (chave.equals("informativo")) {
                c.setInformativo(valor.equalsIgnoreCase("sim"));
            } else {
                c.adicionarRegra(criarRegra(chave, valor));
            }
        }
        lista.add(c);
        return c;
    }

    /* Teste padrão do modem: sempre Prolin e tipo AT, com a espera padrão */
    private void novoAt(List<Comando> lista, String nome, String comandoAt, String descricao, String... linhas) {
        Comando c = novo(lista, Plataforma.PROLIN, nome, comandoAt, descricao, linhas);
        c.setTipo(TipoComando.AT);
    }

    /*
     * "Regras padrão de ping"; só a família (IPv4/IPv6) muda nos rótulos.
     *
     * Por que não usar só "0% packet loss" para o OK?  Porque esse texto também aparece
     * DENTRO de "100% packet loss" ("10" + "0% packet loss"). Um ping que falhou
     * totalmente seria marcado como OK. Por isso o OK exige que antes do "0" venha
     * o início da linha ou um caractere que NÃO seja dígito: (^|[^0-9])0% packet loss.
     * Pelo mesmo motivo, \b100% e \b[1-9][0-9]?% usam \b (fronteira de palavra), para
     * não pegar um pedaço de outro número.
     */
    private String[] regrasPing(String familia) {
        return new String[]{
                "falha=Network is unreachable => Sem rota " + familia,
                "falha=re:\\b100% packet loss => Sem resposta " + familia,
                "alerta=re:\\b[1-9][0-9]?% packet loss => Perda parcial",
                "ok=re:(^|[^0-9])0% packet loss => " + familia + " OK"
        };
    }

    /* Ping por nome: a falha de DNS vem antes das regras de ping */
    private String[] comFalhaDns(String[] regrasPing) {
        String[] todas = new String[regrasPing.length + 1];
        todas[0] = "falha=bad address => Falha de DNS";
        System.arraycopy(regrasPing, 0, todas, 1, regrasPing.length);
        return todas;
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
        String tipo;    // "AT" ou nada
        String espera;  // segundos, só para AT
    }
}
