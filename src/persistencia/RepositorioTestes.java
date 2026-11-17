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
                    case "informativo": bloco.informativo = sim(valor); break;
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

        boolean traduziu = MigracaoTestesPadrao.traduzir(testes);
        boolean completou = completarComPadrao(testes);
        if (traduziu || completou) {
            try {
                salvar(testes);
            } catch (IOException e) {
                System.err.println("Could not save the default tests: " + e.getMessage());
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
            "ANDROID|Network type"
    );

    private boolean informativoSubstituido(Comando c) {
        return INFORMATIVOS_SUBSTITUIDOS.contains(c.getPlataforma().name() + "|" + c.getNome());
    }

    private static boolean sim(String valor) {
        return valor.equalsIgnoreCase("sim") || valor.equalsIgnoreCase("yes");
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isEmpty();
    }

    // Em String Java a barra invertida é dobrada: "\\b" vira \b no arquivo
    public List<Comando> testesPadrao() {
        List<Comando> p = new ArrayList<>();
        final Plataforma PROLIN = Plataforma.PROLIN;
        final Plataforma ANDROID = Plataforma.ANDROID;

        novo(p, PROLIN, "IP addresses", "ip addr show", "Shows the IPs of each interface",
                "falha_sem=re:inet6 [23][0-9a-f]{3}: => No global IPv6",
                "ok=re:inet6 [23][0-9a-f]{3}: => Global IPv6 present");
        novo(p, PROLIN, "LTE interface addresses", "ip addr show seth_lte0", "IPs of the cellular data interface",
                "falha=does not exist => LTE interface missing",
                "falha_sem=re:inet6 [23][0-9a-f]{3}: => LTE without global IPv6",
                "ok=re:inet6 [23][0-9a-f]{3}: => Global IPv6 on LTE");

        novo(p, PROLIN, "IPv4 routes", "ip route show", "Checks for an IPv4 default route",
                "falha_sem=re:(?m)^default => No IPv4 route",
                "ok=re:(?m)^default => IPv4 default route present");

        novo(p, PROLIN, "IPv6 routes", "ip -6 route show", "Checks for an IPv6 default route",
                "falha_sem=re:(?m)^default => No IPv6 route",
                "ok=re:(?m)^default => IPv6 default route present");

        novo(p, PROLIN, "DNS servers", "cat /etc/resolv.conf", "Shows the DNS servers received from the network",
                "falha_sem=re:(?m)^nameserver => No DNS server",
                "ok=re:(?m)^nameserver [0-9a-fA-F]*: => IPv6 DNS configured");

        novo(p, PROLIN, "LTE accepts RA", "cat /proc/sys/net/ipv6/conf/seth_lte0/accept_ra",
                "0 = ignores Router Advertisement",
                "falha=re:(?m)^0$ => Ignores RA",
                "ok=re:(?m)^[12]$ => Accepts RA");

        novo(p, PROLIN, "Kernel IPv6 addresses", "cat /proc/net/if_inet6", "Lists IPv6 straight from the kernel (hex)",
                "informativo=sim");

        novo(p, PROLIN, "Ping IPv4 Google", "ping -c 3 8.8.8.8", "Tests IPv4 connectivity without DNS",
                regrasPing("IPv4"));
        novo(p, PROLIN, "Ping IPv4 by name", "ping -c 3 google.com", "Tests IPv4 and name resolution",
                comFalhaDns(regrasPing("IPv4")));
        novo(p, PROLIN, "Ping IPv6 Google", "ping6 -c 3 2001:4860:4860::8888", "Tests IPv6 connectivity without DNS",
                regrasPing("IPv6"));
        novo(p, PROLIN, "Ping IPv6 over LTE", "ping6 -I seth_lte0 -c 3 2001:4860:4860::8888",
                "Tests IPv6 over the cellular network", regrasPing("IPv6"));
        novo(p, PROLIN, "Ping IPv6 over WiFi", "ping6 -I wlan0 -c 3 2001:4860:4860::8888",
                "Tests IPv6 over the WiFi network", regrasPing("IPv6"));
        novo(p, PROLIN, "Ping IPv6 by name", "ping6 -c 3 google.com", "Tests IPv6 and AAAA resolution",
                comFalhaDns(regrasPing("IPv6")));
        novo(p, PROLIN, "NAT64 test", "ping6 -c 3 64:ff9b::808:808", "Reaches 8.8.8.8 via NAT64",
                regrasNat64());
        novo(p, PROLIN, "DNS64 test", "ping6 -c 3 ipv4only.arpa", "Checks whether DNS synthesizes NAT64 addresses",
                "falha=bad address => No DNS64",
                "ok=re:64:ff9b:: => DNS64 active (well-known prefix)");

        novo(p, PROLIN, "Terminal PN", "/startup/bin/getprop ro.fac.pn", "Terminal part number",
                "informativo=sim");
        novo(p, PROLIN, "Terminal SN", "/startup/bin/getprop ro.fac.sn", "Terminal serial number",
                "informativo=sim");

        testesAt(p, PROLIN, "");

        novo(p, ANDROID, "IP addresses", "ip addr show", "Shows IPs and the CLAT v4- interface",
                "ok=re:v4- => CLAT active",
                "ok=re:inet6 [23][0-9a-f]{3}: => Global IPv6 present");
        novo(p, ANDROID, "IPv4 routes (all tables)", "ip route show table all",
                "Android uses one table per network",
                "falha_sem=re:(?m)^default => No IPv4 route",
                "ok=re:(?m)^default => IPv4 default route present");
        novo(p, ANDROID, "IPv6 routes (all tables)", "ip -6 route show table all",
                "Android uses one table per network",
                "falha_sem=re:(?m)^default => No IPv6 route",
                "ok=re:(?m)^default => IPv6 default route present");
        novo(p, ANDROID, "DNS servers", "dumpsys connectivity | grep -i -E \"DnsAddresses\"",
                "DNS of each active network",
                "informativo=sim");
        novo(p, ANDROID, "CLAT state", "dumpsys connectivity | grep -i -E \"clat|Nat464|v4-\"",
                "Checks CLAT and the NAT64 prefix",
                "informativo=sim");

        novo(p, ANDROID, "Ping IPv4 Google", "ping -c 3 8.8.8.8", "Works via CLAT on IPv6-only networks",
                regrasPing("IPv4"));
        novo(p, ANDROID, "Ping IPv4 by name", "ping -c 3 google.com",
                "Tests IPv4 and DNS (works via CLAT on IPv6-only networks)",
                comFalhaDns("unknown host", regrasPing("IPv4")));
        novo(p, ANDROID, "Ping IPv6 Google", "ping6 -c 3 2001:4860:4860::8888", "Tests IPv6 connectivity without DNS",
                regrasPing("IPv6"));
        novo(p, ANDROID, "Ping IPv6 by name", "ping6 -c 3 google.com", "Tests IPv6 and AAAA resolution",
                comFalhaDns("unknown host", regrasPing("IPv6")));
        novo(p, ANDROID, "NAT64 test", "ping6 -c 3 64:ff9b::808:808", "Reaches 8.8.8.8 via NAT64",
                regrasNat64());
        novo(p, ANDROID, "DNS64 test", "ping6 -c 3 ipv4only.arpa", "Checks whether DNS synthesizes NAT64 addresses",
                "falha=unknown host => No DNS64",
                "ok=re:64:ff9b:: => DNS64 active (well-known prefix)");

        novo(p, ANDROID, "Modem - Version (baseband)", "getprop gsm.version.baseband",
                "Equivalent to AT+CGMR",
                "informativo=sim");
        novo(p, ANDROID, "Modem - SIM status", "getprop gsm.sim.state",
                "Equivalent to AT+CPIN? (dual SIM values are comma-separated; slot 1 counts)",
                "falha=re:(?m)^ABSENT => No SIM in slot 1",
                "falha=re:(?m)^(PIN_REQUIRED|PUK_REQUIRED) => SIM locked",
                "ok=re:(?m)^READY => SIM ready");
        novo(p, ANDROID, "Modem - Carrier", "getprop gsm.operator.alpha",
                "Equivalent to the name in AT+COPS?",
                "informativo=sim");
        novo(p, ANDROID, "Network type", "getprop gsm.network.type",
                "Equivalent to the AT+COPS? technology (slot 1 counts)",
                "falha=re:(?m)^Unknown => No data network",
                "alerta=re:(?m)^(EDGE|GPRS|GSM) => 2G network",
                "alerta=re:(?m)^(UMTS|HSDPA|HSUPA|HSPA\\+?) => 3G network",
                "ok=re:(?m)^(LTE|LTE_CA|NR) => 4G/5G network");
        novo(p, ANDROID, "Modem - Data registration", "dumpsys telephony.registry | grep -i -E \"mDataRegState\"",
                "Equivalent to AT+CEREG?",
                "falha=re:mDataRegState=[123]\\( => Data out of service",
                "ok=re:mDataRegState=0\\(IN_SERVICE\\) => Data in service");
        novo(p, ANDROID, "Modem - Data technology",
                "dumpsys telephony.registry | grep -i -E \"mDataNetworkType|rilDataRadioTechnology\"",
                "Technology used for data",
                "informativo=sim");
        novo(p, ANDROID, "Modem - Signal", "dumpsys telephony.registry | grep -i -E \"mSignalStrength\"",
                "Equivalent to AT+CSQ",
                "informativo=sim");

        testesAt(p, ANDROID, MigracaoTestesPadrao.SUFIXO_AT_ANDROID);

        return p;
    }

    private void testesAt(List<Comando> p, Plataforma plataforma, String sufixo) {
        novoAt(p, plataforma, "AT - Modem version", "AT+CGMR", "Modem firmware" + sufixo);
        novoAt(p, plataforma, "AT - Manufacturer", "AT+CGMI", "Modem manufacturer" + sufixo);
        novoAt(p, plataforma, "AT - Model", "AT+CGMM", "Modem model" + sufixo);
        novoAt(p, plataforma, "AT - IMEI", "AT+CGSN", "Modem IMEI" + sufixo);
        novoAt(p, plataforma, "AT - IMSI", "AT+CIMI", "SIM identity at the carrier" + sufixo);
        novoAt(p, plataforma, "AT - SIM status", "AT+CPIN?", "READY = SIM ready" + sufixo,
                "falha=re:not inserted|SIM failure|NOT INSERTED => No SIM",
                "falha=SIM PIN => SIM locked by PIN",
                "falha=SIM PUK => SIM locked by PUK",
                "ok=READY => SIM ready");
        novoAt(p, plataforma, "AT - Radio on", "AT+CFUN?", "1 = radio on" + sufixo,
                "falha=re:\\+CFUN: [04]\\b => Radio off or airplane mode",
                "ok=re:\\+CFUN: 1\\b => Radio on");
        novoAt(p, plataforma, "AT - Signal", "AT+CSQ", "RSSI 0-31 (99 = unknown)" + sufixo,
                "falha=re:\\+CSQ: 99, => No signal",
                "alerta=re:\\+CSQ: [0-9], => Weak signal",
                "ok=re:\\+CSQ: (1[0-9]|2[0-9]|3[01]), => Good signal");
        novoAt(p, plataforma, "AT - Carrier and technology", "AT+COPS?", "Last number: 0/3 = 2G, 2/4/5/6 = 3G, 7 = 4G" + sufixo,
                "falha=re:(?m)^\\+COPS: [0-9]$ => No carrier",
                "alerta=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",[03]\\b => 2G network",
                "alerta=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",[2456]\\b => 3G network",
                "ok=re:\\+COPS: [0-9],[0-9],\"[^\"]*\",7\\b => 4G network (LTE)");
        novoAt(p, plataforma, "AT - LTE registration", "AT+CEREG?", "Registration on the 4G network" + sufixo,
                "falha=re:\\+CEREG: [0-9],3\\b => Registration denied",
                "falha=re:\\+CEREG: [0-9],[04]\\b => Not registered",
                "alerta=re:\\+CEREG: [0-9],2\\b => Searching for network",
                "ok=re:\\+CEREG: [0-9],[15]\\b => Registered on LTE");
        novoAt(p, plataforma, "AT - 2G/3G registration", "AT+CREG?", "Registration on the 2G/3G network" + sufixo,
                "falha=re:\\+CREG: [0-9],3\\b => Registration denied",
                "falha=re:\\+CREG: [0-9],[04]\\b => Not registered",
                "alerta=re:\\+CREG: [0-9],2\\b => Searching for network",
                "ok=re:\\+CREG: [0-9],[15]\\b => Registered");
        novoAt(p, plataforma, "AT - Configured APN", "AT+CGDCONT?", "PDP type: IP, IPV6 or IPV4V6" + sufixo,
                "alerta=re:\"IP\", => IPv4-only APN",
                "ok=re:\"IPV4V6\" => Dual-stack APN",
                "ok=re:\"IPV6\" => IPv6-only APN");
        novoAt(p, plataforma, "AT - Active data context", "AT+CGACT?", "Checks for an active data context" + sufixo,
                "falha_sem=re:\\+CGACT: [0-9]+,1\\b => No active context",
                "ok=re:\\+CGACT: [0-9]+,1\\b => Data context active");
        novoAt(p, plataforma, "AT - Context address", "AT+CGPADDR", "IP assigned by the carrier to each context" + sufixo,
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
                c.setInformativo(sim(valor));
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
                "falha=Network is unreachable => No " + familia + " route",
                "falha=re:\\b100% packet loss => No " + familia + " response",
                "alerta=re:\\b[1-9][0-9]?% packet loss => Partial loss",
                "ok=re:(^|[^0-9])0% packet loss => " + familia + " OK"
        };
    }

    private String[] comFalhaDns(String[] regrasPing) {
        return comFalhaDns("bad address", regrasPing);
    }

    private String[] comFalhaDns(String textoErro, String[] regrasPing) {
        String[] todas = new String[regrasPing.length + 1];
        todas[0] = "falha=" + textoErro + " => DNS failure";
        System.arraycopy(regrasPing, 0, todas, 1, regrasPing.length);
        return todas;
    }

    private String[] regrasNat64() {
        return new String[]{
                "falha=Network is unreachable => No IPv6 route",
                "falha=re:\\b100% packet loss => NAT64 unavailable",
                "alerta=re:\\b[1-9][0-9]?% packet loss => Partial loss",
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
