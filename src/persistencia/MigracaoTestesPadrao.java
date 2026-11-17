package persistencia;

import avaliacao.Regra;
import model.Comando;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Os testes padrão eram gravados em português no testes.txt. Ao carregar, nomes, descrições e rótulos
// que ainda estão iguais ao texto antigo são trocados pela versão em inglês; o que o usuário escreveu fica igual.
public final class MigracaoTestesPadrao {

    private static final String SUFIXO_AT_ANDROID_PT = " (requer root e porta AT no config.txt)";
    public static final String SUFIXO_AT_ANDROID = " (requires root and AT port in config.txt)";

    private static final String[][] PARES = {
            // Nomes
            {"Endereços IP", "IP addresses"},
            {"Endereços da interface LTE", "LTE interface addresses"},
            {"Rotas IPv4", "IPv4 routes"},
            {"Rotas IPv6", "IPv6 routes"},
            {"Rotas IPv4 (todas as tabelas)", "IPv4 routes (all tables)"},
            {"Rotas IPv6 (todas as tabelas)", "IPv6 routes (all tables)"},
            {"Servidores DNS", "DNS servers"},
            {"Aceita RA no LTE", "LTE accepts RA"},
            {"Endereços IPv6 do kernel", "Kernel IPv6 addresses"},
            {"Verificar CLAT", "Check CLAT"},
            {"Estado do CLAT", "CLAT state"},
            {"Ping IPv4 por nome", "Ping IPv4 by name"},
            {"Ping IPv6 por nome", "Ping IPv6 by name"},
            {"Ping IPv6 pelo LTE", "Ping IPv6 over LTE"},
            {"Ping IPv6 pelo WiFi", "Ping IPv6 over WiFi"},
            {"Teste NAT64", "NAT64 test"},
            {"Teste DNS64", "DNS64 test"},
            {"PN do terminal", "Terminal PN"},
            {"SN do terminal", "Terminal SN"},
            {"Modem - Versão (baseband)", "Modem - Version (baseband)"},
            {"Modem - Status do SIM", "Modem - SIM status"},
            {"Modem - Operadora", "Modem - Carrier"},
            {"Tipo de rede", "Network type"},
            {"Modem - Registro de dados", "Modem - Data registration"},
            {"Modem - Tecnologia de dados", "Modem - Data technology"},
            {"Modem - Sinal", "Modem - Signal"},
            {"AT - Versão do modem", "AT - Modem version"},
            {"AT - Fabricante", "AT - Manufacturer"},
            {"AT - Modelo", "AT - Model"},
            {"AT - Status do SIM", "AT - SIM status"},
            {"AT - Rádio ligado", "AT - Radio on"},
            {"AT - Sinal", "AT - Signal"},
            {"AT - Operadora e tecnologia", "AT - Carrier and technology"},
            {"AT - Registro LTE", "AT - LTE registration"},
            {"AT - Registro 2G/3G", "AT - 2G/3G registration"},
            {"AT - APN configurada", "AT - Configured APN"},
            {"AT - Contexto de dados ativo", "AT - Active data context"},
            {"AT - Endereço do contexto", "AT - Context address"},

            // Descrições
            {"Mostra os IPs de cada interface", "Shows the IPs of each interface"},
            {"IPs da interface de dados celular", "IPs of the cellular data interface"},
            {"Verifica se existe rota default IPv4", "Checks for an IPv4 default route"},
            {"Verifica se existe rota default IPv6", "Checks for an IPv6 default route"},
            {"Mostra os DNS recebidos da rede", "Shows the DNS servers received from the network"},
            {"0 = ignora Router Advertisement", "0 = ignores Router Advertisement"},
            {"Lista IPv6 direto do kernel (hex)", "Lists IPv6 straight from the kernel (hex)"},
            {"Prolin não tem CLAT; o esperado é Sem CLAT", "Prolin has no CLAT; no CLAT is expected"},
            {"Testa conectividade IPv4 sem DNS", "Tests IPv4 connectivity without DNS"},
            {"Testa IPv4 e resolução de nomes", "Tests IPv4 and name resolution"},
            {"Testa conectividade IPv6 sem DNS", "Tests IPv6 connectivity without DNS"},
            {"Testa IPv6 pela rede celular", "Tests IPv6 over the cellular network"},
            {"Testa IPv6 pela rede WiFi", "Tests IPv6 over the WiFi network"},
            {"Testa IPv6 e resolução AAAA", "Tests IPv6 and AAAA resolution"},
            {"Alcança o 8.8.8.8 via NAT64", "Reaches 8.8.8.8 via NAT64"},
            {"Verifica se o DNS sintetiza endereços NAT64", "Checks whether DNS synthesizes NAT64 addresses"},
            {"Part number do terminal", "Terminal part number"},
            {"Número de série do terminal", "Terminal serial number"},
            {"Mostra IPs e interface v4- do CLAT", "Shows IPs and the CLAT v4- interface"},
            {"Android usa uma tabela por rede", "Android uses one table per network"},
            {"DNS de cada rede ativa", "DNS of each active network"},
            {"Verifica CLAT e prefixo NAT64", "Checks CLAT and the NAT64 prefix"},
            {"Em rede IPv6-only funciona via CLAT", "Works via CLAT on IPv6-only networks"},
            {"Testa IPv4 e DNS (funciona via CLAT em rede IPv6-only)",
                    "Tests IPv4 and DNS (works via CLAT on IPv6-only networks)"},
            {"Equivalente ao AT+CGMR", "Equivalent to AT+CGMR"},
            {"Equivalente ao AT+CPIN? (dual SIM vem separado por vírgula; vale o slot 1)",
                    "Equivalent to AT+CPIN? (dual SIM values are comma-separated; slot 1 counts)"},
            {"Equivalente ao nome no AT+COPS?", "Equivalent to the name in AT+COPS?"},
            {"Equivalente à tecnologia do AT+COPS? (vale o slot 1)",
                    "Equivalent to the AT+COPS? technology (slot 1 counts)"},
            {"Equivalente ao AT+CEREG?", "Equivalent to AT+CEREG?"},
            {"Tecnologia usada pelos dados", "Technology used for data"},
            {"Equivalente ao AT+CSQ", "Equivalent to AT+CSQ"},
            {"Firmware do modem", "Modem firmware"},
            {"Fabricante do modem", "Modem manufacturer"},
            {"Modelo do modem", "Modem model"},
            {"IMEI do modem", "Modem IMEI"},
            {"Identificação do SIM na operadora", "SIM identity at the carrier"},
            {"READY = SIM pronto", "READY = SIM ready"},
            {"1 = rádio ligado", "1 = radio on"},
            {"RSSI 0-31 (99 = desconhecido)", "RSSI 0-31 (99 = unknown)"},
            {"Último número: 0/3 = 2G, 2/4/5/6 = 3G, 7 = 4G", "Last number: 0/3 = 2G, 2/4/5/6 = 3G, 7 = 4G"},
            {"Registro na rede 4G", "Registration on the 4G network"},
            {"Registro na rede 2G/3G", "Registration on the 2G/3G network"},
            {"Tipo de PDP: IP, IPV6 ou IPV4V6", "PDP type: IP, IPV6 or IPV4V6"},
            {"Verifica se há contexto de dados ativo", "Checks for an active data context"},
            {"IP entregue pela operadora em cada contexto", "IP assigned by the carrier to each context"},

            // Rótulos das regras
            {"Sem IPv6 global", "No global IPv6"},
            {"IPv6 global presente", "Global IPv6 present"},
            {"Interface LTE inexistente", "LTE interface missing"},
            {"LTE sem IPv6 global", "LTE without global IPv6"},
            {"IPv6 global no LTE", "Global IPv6 on LTE"},
            {"Sem rota IPv4", "No IPv4 route"},
            {"Sem rota IPv6", "No IPv6 route"},
            {"Rota default IPv4 presente", "IPv4 default route present"},
            {"Rota default IPv6 presente", "IPv6 default route present"},
            {"Sem servidor DNS", "No DNS server"},
            {"DNS IPv6 configurado", "IPv6 DNS configured"},
            {"Ignora RA", "Ignores RA"},
            {"Aceita RA", "Accepts RA"},
            {"Interface CLAT encontrada", "CLAT interface found"},
            {"Sem CLAT (esperado no Prolin)", "No CLAT (expected on Prolin)"},
            {"Sem resposta IPv4", "No IPv4 response"},
            {"Sem resposta IPv6", "No IPv6 response"},
            {"Perda parcial", "Partial loss"},
            {"Falha de DNS", "DNS failure"},
            {"NAT64 indisponível", "NAT64 unavailable"},
            {"Sem DNS64", "No DNS64"},
            {"DNS64 ativo (prefixo padrão)", "DNS64 active (well-known prefix)"},
            {"CLAT ativo", "CLAT active"},
            {"Sem SIM no slot 1", "No SIM in slot 1"},
            {"SIM bloqueado", "SIM locked"},
            {"SIM pronto", "SIM ready"},
            {"Sem rede de dados", "No data network"},
            {"Rede 2G", "2G network"},
            {"Rede 3G", "3G network"},
            {"Rede 4G/5G", "4G/5G network"},
            {"Dados fora de serviço", "Data out of service"},
            {"Dados em serviço", "Data in service"},
            {"Sem SIM", "No SIM"},
            {"SIM bloqueado por PIN", "SIM locked by PIN"},
            {"SIM bloqueado por PUK", "SIM locked by PUK"},
            {"Rádio desligado ou modo avião", "Radio off or airplane mode"},
            {"Rádio ligado", "Radio on"},
            {"Sem sinal", "No signal"},
            {"Sinal fraco", "Weak signal"},
            {"Sinal bom", "Good signal"},
            {"Sem operadora", "No carrier"},
            {"Rede 4G (LTE)", "4G network (LTE)"},
            {"Registro negado", "Registration denied"},
            {"Não registrado", "Not registered"},
            {"Procurando rede", "Searching for network"},
            {"Registrado no LTE", "Registered on LTE"},
            {"Registrado", "Registered"},
            {"APN só IPv4", "IPv4-only APN"},
            {"APN dual-stack", "Dual-stack APN"},
            {"APN IPv6-only", "IPv6-only APN"},
            {"Nenhum contexto ativo", "No active context"},
            {"Contexto de dados ativo", "Data context active"},
    };

    private static final Map<String, String> TRADUCOES = new HashMap<>();

    static {
        for (String[] par : PARES) {
            TRADUCOES.put(par[0], par[1]);
        }
    }

    private MigracaoTestesPadrao() {
    }

    // Retorna true se algo mudou (o chamador grava o arquivo)
    public static boolean traduzir(List<Comando> testes) {
        boolean mudou = false;
        for (Comando c : testes) {
            String nome = traduzirTexto(c.getNome());
            if (!nome.equals(c.getNome())) {
                c.setNome(nome);
                mudou = true;
            }

            String descricao = traduzirTexto(c.getDescricao());
            if (!descricao.equals(c.getDescricao())) {
                c.setDescricao(descricao);
                mudou = true;
            }

            List<Regra> regras = new ArrayList<>();
            boolean regraMudou = false;
            for (Regra r : c.getRegras()) {
                String rotulo = traduzirTexto(r.getRotulo());
                if (rotulo.equals(r.getRotulo())) {
                    regras.add(r);
                } else {
                    regras.add(new Regra(r.getTipo(), r.getPadrao(), rotulo, r.isRegex(), r.isAusencia()));
                    regraMudou = true;
                }
            }
            if (regraMudou) {
                c.setRegras(regras);
                mudou = true;
            }
        }
        return mudou;
    }

    private static String traduzirTexto(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto == null ? "" : texto;
        }
        String direto = TRADUCOES.get(texto);
        if (direto != null) {
            return direto;
        }
        // Descrições dos AT no Android têm um sufixo
        if (texto.endsWith(SUFIXO_AT_ANDROID_PT)) {
            String base = texto.substring(0, texto.length() - SUFIXO_AT_ANDROID_PT.length());
            String traduzida = TRADUCOES.get(base);
            return (traduzida == null ? base : traduzida) + SUFIXO_AT_ANDROID;
        }
        return texto;
    }
}
