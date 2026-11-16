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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RepositorioTestes {

    private static final String INICIO_BLOCO = "[teste]";

    private final Path pasta;
    private final Path arquivo;

    public RepositorioTestes() {
        pasta = PastaDados.obter();
        arquivo = pasta.resolve("testes.txt");
    }

    public Path getArquivo() {
        return arquivo;
    }

    public List<Comando> carregar() throws IOException {
        if (!Files.exists(arquivo)) {
            List<Comando> padrao = testesPadrao();
            salvar(padrao);
            return padrao;
        }

        List<Comando> testes = new ArrayList<>();
        Bloco bloco = null;

        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String linha;
            boolean primeiraLinha = true;

            while ((linha = leitor.readLine()) != null) {
                if (primeiraLinha && linha.startsWith("\uFEFF")) {
                    linha = linha.substring(1);
                }
                primeiraLinha = false;

                String limpa = linha.trim();

                if (limpa.isEmpty() || limpa.startsWith("#")) {
                    continue;
                }

                if (limpa.equals(INICIO_BLOCO)) {
                    adicionarSeValido(bloco, testes);
                    bloco = new Bloco();
                    continue;
                }

                if (bloco == null) {
                    continue;
                }

                // Separa no primeiro "=": o comando pode conter outros
                int posicao = linha.indexOf('=');
                if (posicao < 0) {
                    continue;
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

        adicionarSeValido(bloco, testes);

        if (completarComPadrao(testes)) {
            try {
                salvar(testes);
            } catch (IOException e) {
                System.err.println("Não foi possível gravar os testes padrão: " + e.getMessage());
            }
        }
        return testes;
    }

    public void salvar(List<Comando> testes) throws IOException {
        Files.createDirectories(pasta);

        try (BufferedWriter escritor = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            boolean primeiro = true;
            for (Comando c : testes) {
                if (!primeiro) {
                    escritor.newLine();
                }
                primeiro = false;

                escritor.write(INICIO_BLOCO);
                escritor.newLine();
                escreverCampo(escritor, "nome", c.getNome());
                escreverCampo(escritor, "plataforma", c.getPlataforma().name());
                escreverCampo(escritor, "comando", c.getTxtComando());
                escreverCampo(escritor, "descricao", c.getDescricao());

                if (c.isAt()) {
                    escreverCampo(escritor, "tipo", TipoComando.AT.name());
                    escreverCampo(escritor, "espera", String.valueOf(c.getEsperaSegundos()));
                }

                if (c.isInformativo()) {
                    escreverCampo(escritor, "informativo", "sim");
                }
                for (Regra r : c.getRegras()) {
                    escreverCampo(escritor, r.getChave(), r.paraTexto());
                }
            }
        }
    }

    private void escreverCampo(BufferedWriter escritor, String chave, String valor) throws IOException {
        if (valor == null) {
            valor = "";
        }
        valor = valor.replace("\r", " ").replace("\n", " ");
        escritor.write(chave + "=" + valor);
        escritor.newLine();
    }

    private static Regra criarRegra(String chave, String valor) {
        switch (chave) {
            case "ok":        return Regra.deTexto(Veredito.OK, valor);
            case "alerta":    return Regra.deTexto(Veredito.ALERTA, valor);
            case "falha":     return Regra.deTexto(Veredito.FALHA, valor);
            case "falha_sem": return Regra.deTextoAusencia(valor);
            default:          return null;
        }
    }

    private void adicionarSeValido(Bloco bloco, List<Comando> testes) {
        if (bloco == null) {
            return;
        }
        if (vazio(bloco.nome) || vazio(bloco.plataforma) || vazio(bloco.comando)) {
            return;
        }

        Plataforma plataforma;
        try {
            plataforma = Plataforma.valueOf(bloco.plataforma.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        String descricao = (bloco.descricao == null) ? "" : bloco.descricao;
        Comando comando = new Comando(bloco.nome, plataforma, bloco.comando, descricao);
        comando.setRegras(bloco.regras);
        comando.setInformativo(bloco.informativo);

        if (bloco.tipo != null && bloco.tipo.equalsIgnoreCase("AT")) {
            comando.setTipo(TipoComando.AT);
        }
        if (bloco.espera != null) {
            try {
                comando.setEsperaSegundos(Integer.valueOf(bloco.espera));
            } catch (NumberFormatException e) {
            }
        }
        testes.add(comando);
    }

    // Acrescenta padrões que faltam e dá regras a padrões sem regras; testes do usuário não mudam
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
                testes.add(p);
                mudou = true;
                continue;
            }

            boolean existenteSemRegras = existente.getRegras().isEmpty()
                    && (!existente.isInformativo() || informativoSubstituido(existente));
            boolean padraoTemRegras = !p.getRegras().isEmpty() || p.isInformativo();
            if (existenteSemRegras && padraoTemRegras) {
                existente.setRegras(p.getRegras());
                existente.setInformativo(p.isInformativo());
                mudou = true;
            }
        }
        return mudou;
    }

    // Padrões que eram só informativos e ganharam regras (formato PLATAFORMA|nome)
    private static final Set<String> INFORMATIVOS_SUBSTITUIDOS = Set.of(
            "ANDROID|Tipo de rede"
    );

    private boolean informativoSubstituido(Comando c) {
        return INFORMATIVOS_SUBSTITUIDOS.contains(c.getPlataforma().name() + "|" + c.getNome());
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isEmpty();
    }

    // Em String Java a barra invertida é dobrada: "\\b" vira \b no arquivo
    public List<Comando> testesPadrao() {
        List<Comando> p = new ArrayList<>();
        final Plataforma PROLIN = Plataforma.PROLIN;
        final Plataforma ANDROID = Plataforma.ANDROID;

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
                regrasNat64());
        novo(p, PROLIN, "Teste DNS64", "ping6 -c 3 ipv4only.arpa", "Verifica se o DNS sintetiza endereços NAT64",
                "falha=bad address => Sem DNS64",
                "ok=re:64:ff9b:: => DNS64 ativo (prefixo padrão)");

        novo(p, PROLIN, "PN do terminal", "/startup/bin/getprop ro.fac.pn", "Part number do terminal",
                "informativo=sim");
        novo(p, PROLIN, "SN do terminal", "/startup/bin/getprop ro.fac.sn", "Número de série do terminal",
                "informativo=sim");

        testesAt(p, PROLIN, "");

        novo(p, ANDROID, "Endereços IP", "ip addr show", "Mostra IPs e interface v4- do CLAT",
                "ok=re:v4- => CLAT ativo",
                "ok=re:inet6 [23][0-9a-f]{3}: => IPv6 global presente");
        novo(p, ANDROID, "Rotas IPv4 (todas as tabelas)", "ip route show table all",
                "Android usa uma tabela por rede",
                "falha_sem=re:(?m)^default => Sem rota IPv4",
                "ok=re:(?m)^default => Rota default IPv4 presente");
        novo(p, ANDROID, "Rotas IPv6 (todas as tabelas)", "ip -6 route show table all",
                "Android usa uma tabela por rede",
                "falha_sem=re:(?m)^default => Sem rota IPv6",
                "ok=re:(?m)^default => Rota default IPv6 presente");
        novo(p, ANDROID, "Servidores DNS", "dumpsys connectivity | grep -i -E \"DnsAddresses\"",
                "DNS de cada rede ativa",
                "informativo=sim");
        novo(p, ANDROID, "Estado do CLAT", "dumpsys connectivity | grep -i -E \"clat|Nat464|v4-\"",
                "Verifica CLAT e prefixo NAT64",
                "informativo=sim");

        novo(p, ANDROID, "Ping IPv4 Google", "ping -c 3 8.8.8.8", "Em rede IPv6-only funciona via CLAT",
                regrasPing("IPv4"));
        novo(p, ANDROID, "Ping IPv4 por nome", "ping -c 3 google.com",
                "Testa IPv4 e DNS (funciona via CLAT em rede IPv6-only)",
                comFalhaDns("unknown host", regrasPing("IPv4")));
        novo(p, ANDROID, "Ping IPv6 Google", "ping6 -c 3 2001:4860:4860::8888", "Testa conectividade IPv6 sem DNS",
                regrasPing("IPv6"));
        novo(p, ANDROID, "Ping IPv6 por nome", "ping6 -c 3 google.com", "Testa IPv6 e resolução AAAA",
                comFalhaDns("unknown host", regrasPing("IPv6")));
        novo(p, ANDROID, "Teste NAT64", "ping6 -c 3 64:ff9b::808:808", "Alcança o 8.8.8.8 via NAT64",
                regrasNat64());
        novo(p, ANDROID, "Teste DNS64", "ping6 -c 3 ipv4only.arpa", "Verifica se o DNS sintetiza endereços NAT64",
                "falha=unknown host => Sem DNS64",
                "ok=re:64:ff9b:: => DNS64 ativo (prefixo padrão)");

        novo(p, ANDROID, "Modem - Versão (baseband)", "getprop gsm.version.baseband",
                "Equivalente ao AT+CGMR",
                "informativo=sim");
        novo(p, ANDROID, "Modem - Status do SIM", "getprop gsm.sim.state",
                "Equivalente ao AT+CPIN? (dual SIM vem separado por vírgula; vale o slot 1)",
                "falha=re:(?m)^ABSENT => Sem SIM no slot 1",
                "falha=re:(?m)^(PIN_REQUIRED|PUK_REQUIRED) => SIM bloqueado",
                "ok=re:(?m)^READY => SIM pronto");
        novo(p, ANDROID, "Modem - Operadora", "getprop gsm.operator.alpha",
                "Equivalente ao nome no AT+COPS?",
                "informativo=sim");
        novo(p, ANDROID, "Tipo de rede", "getprop gsm.network.type",
                "Equivalente à tecnologia do AT+COPS? (vale o slot 1)",
                "falha=re:(?m)^Unknown => Sem rede de dados",
                "alerta=re:(?m)^(EDGE|GPRS|GSM) => Rede 2G",
                "alerta=re:(?m)^(UMTS|HSDPA|HSUPA|HSPA\\+?) => Rede 3G",
                "ok=re:(?m)^(LTE|LTE_CA|NR) => Rede 4G/5G");
        novo(p, ANDROID, "Modem - Registro de dados", "dumpsys telephony.registry | grep -i -E \"mDataRegState\"",
                "Equivalente ao AT+CEREG?",
                "falha=re:mDataRegState=[123]\\( => Dados fora de serviço",
                "ok=re:mDataRegState=0\\(IN_SERVICE\\) => Dados em serviço");
        novo(p, ANDROID, "Modem - Tecnologia de dados",
                "dumpsys telephony.registry | grep -i -E \"mDataNetworkType|rilDataRadioTechnology\"",
                "Tecnologia usada pelos dados",
                "informativo=sim");
        novo(p, ANDROID, "Modem - Sinal", "dumpsys telephony.registry | grep -i -E \"mSignalStrength\"",
                "Equivalente ao AT+CSQ",
                "informativo=sim");

        testesAt(p, ANDROID, " (requer root e porta AT no config.txt)");

        return p;
    }

    private void testesAt(List<Comando> p, Plataforma plataforma, String sufixo) {
        novoAt(p, plataforma, "AT - Versão do modem", "AT+CGMR", "Firmware do modem" + sufixo);
        novoAt(p, plataforma, "AT - Fabricante", "AT+CGMI", "Fabricante do modem" + sufixo);
        novoAt(p, plataforma, "AT - Modelo", "AT+CGMM", "Modelo do modem" + sufixo);
        novoAt(p, plataforma, "AT - IMEI", "AT+CGSN", "IMEI do modem" + sufixo);
        novoAt(p, plataforma, "AT - IMSI", "AT+CIMI", "Identificação do SIM na operadora" + sufixo);
        novoAt(p, plataforma, "AT - Status do SIM", "AT+CPIN?", "READY = SIM pronto" + sufixo,
                "falha=re:not inserted|SIM failure|NOT INSERTED => Sem SIM",
                "falha=SIM PIN => SIM bloqueado por PIN",
                "falha=SIM PUK => SIM bloqueado por PUK",
                "ok=READY => SIM pronto");
        novoAt(p, plataforma, "AT - Rádio ligado", "AT+CFUN?", "1 = rádio ligado" + sufixo,
                "falha=re:\\+CFUN: [04]\\b => Rádio desligado ou modo avião",
                "ok=re:\\+CFUN: 1\\b => Rádio ligado");
        novoAt(p, plataforma, "AT - Sinal", "AT+CSQ", "RSSI 0-31 (99 = desconhecido)" + sufixo,
                "falha=re:\\+CSQ: 99, => Sem sinal",
                "alerta=re:\\+CSQ: [0-9], => Sinal fraco",
                "ok=re:\\+CSQ: (1[0-9]|2[0-9]|3[01]), => Sinal bom");
        novoAt(p, plataforma, "AT - Operadora e tecnologia", "AT+COPS?", "Último número: 0/3 = 2G, 2/4/5/6 = 3G, 7 = 4G" + sufixo,
                "falha=re:(?m)^\\+COPS: [0-9]$ => Sem operadora",
                "alerta=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",[03]\\b => Rede 2G",
                "alerta=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",[2456]\\b => Rede 3G",
                "ok=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",7\\b => Rede 4G (LTE)");
        novoAt(p, plataforma, "AT - Registro LTE", "AT+CEREG?", "Registro na rede 4G" + sufixo,
                "falha=re:\\+CEREG: [0-9],3\\b => Registro negado",
                "falha=re:\\+CEREG: [0-9],[04]\\b => Não registrado",
                "alerta=re:\\+CEREG: [0-9],2\\b => Procurando rede",
                "ok=re:\\+CEREG: [0-9],[15]\\b => Registrado no LTE");
        novoAt(p, plataforma, "AT - Registro 2G/3G", "AT+CREG?", "Registro na rede 2G/3G" + sufixo,
                "falha=re:\\+CREG: [0-9],3\\b => Registro negado",
                "falha=re:\\+CREG: [0-9],[04]\\b => Não registrado",
                "alerta=re:\\+CREG: [0-9],2\\b => Procurando rede",
                "ok=re:\\+CREG: [0-9],[15]\\b => Registrado");
        novoAt(p, plataforma, "AT - APN configurada", "AT+CGDCONT?", "Tipo de PDP: IP, IPV6 ou IPV4V6" + sufixo,
                "alerta=re:\"IP\", => APN só IPv4",
                "ok=re:\"IPV4V6\" => APN dual-stack",
                "ok=re:\"IPV6\" => APN IPv6-only");
        novoAt(p, plataforma, "AT - Contexto de dados ativo", "AT+CGACT?", "Verifica se há contexto de dados ativo" + sufixo,
                "falha_sem=re:\\+CGACT: [0-9]+,1\\b => Nenhum contexto ativo",
                "ok=re:\\+CGACT: [0-9]+,1\\b => Contexto de dados ativo");
        novoAt(p, plataforma, "AT - Endereço do contexto", "AT+CGPADDR", "IP entregue pela operadora em cada contexto" + sufixo,
                "informativo=sim");
    }

    private Comando novo(List<Comando> lista, Plataforma plataforma, String nome, String comando,
                         String descricao, String... linhas) {
        Comando c = new Comando(nome, plataforma, comando, descricao);
        for (String linha : linhas) {
            // Separa no primeiro "=": o comando pode conter outros
            int posicao = linha.indexOf('=');
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

    private void novoAt(List<Comando> lista, Plataforma plataforma, String nome, String comandoAt,
                        String descricao, String... linhas) {
        Comando c = novo(lista, plataforma, nome, comandoAt, descricao, linhas);
        c.setTipo(TipoComando.AT);
    }

    // OK exige (^|[^0-9])0% porque "0% packet loss" também aparece dentro de "100% packet loss"
    private String[] regrasPing(String familia) {
        return new String[]{
                "falha=Network is unreachable => Sem rota " + familia,
                "falha=re:\\b100% packet loss => Sem resposta " + familia,
                "alerta=re:\\b[1-9][0-9]?% packet loss => Perda parcial",
                "ok=re:(^|[^0-9])0% packet loss => " + familia + " OK"
        };
    }

    private String[] comFalhaDns(String[] regrasPing) {
        return comFalhaDns("bad address", regrasPing);
    }

    private String[] comFalhaDns(String textoErro, String[] regrasPing) {
        String[] todas = new String[regrasPing.length + 1];
        todas[0] = "falha=" + textoErro + " => Falha de DNS";
        System.arraycopy(regrasPing, 0, todas, 1, regrasPing.length);
        return todas;
    }

    private String[] regrasNat64() {
        return new String[]{
                "falha=Network is unreachable => Sem rota IPv6",
                "falha=re:\\b100% packet loss => NAT64 indisponível",
                "alerta=re:\\b[1-9][0-9]?% packet loss => Perda parcial",
                "ok=re:(^|[^0-9])0% packet loss => NAT64 OK"
        };
    }

    private static class Bloco {
        String nome;
        String plataforma;
        String comando;
        String descricao;
        List<Regra> regras = new ArrayList<>();
        boolean informativo = false;
        String tipo;
        String espera;
    }
}
