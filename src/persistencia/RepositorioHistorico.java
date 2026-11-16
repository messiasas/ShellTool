package persistencia;

import avaliacao.ExecucaoAvaliada;
import avaliacao.RegistroTeste;
import avaliacao.Veredito;
import model.Plataforma;
import model.ResultadoExecucao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class RepositorioHistorico {

    private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path pasta;

    public RepositorioHistorico() {
        pasta = PastaDados.obter().resolve("historico");
    }

    public static String limparNome(String nome) {
        return nome.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    public Path caminhoPara(String nomeLimpo, LocalDateTime inicio) {
        return pasta.resolve(nomeLimpo + "-" + inicio.format(FORMATO_NOME) + ".txt");
    }

    public Path proximoLivre(Path caminho) {
        String nome = caminho.getFileName().toString();
        String base = nome.substring(0, nome.length() - ".txt".length());
        int n = 2;
        Path candidato = caminho;
        while (Files.exists(candidato)) {
            candidato = caminho.resolveSibling(base + " (" + n + ").txt");
            n++;
        }
        return candidato;
    }

    public void salvar(Path caminho, RegistroTeste registro, String nomeTeste, LocalDateTime fim)
            throws IOException {
        Files.createDirectories(pasta);
        String conteudo = montarConteudo(registro, nomeTeste, fim);
        conteudo = conteudo.replace("\n", "\r\n");
        Files.writeString(caminho, conteudo, StandardCharsets.UTF_8);
    }

    private String montarConteudo(RegistroTeste registro, String nomeTeste, LocalDateTime fim) {
        StringBuilder t = new StringBuilder();
        t.append("=== Registro de teste: ").append(nomeTeste).append(" ===\n");
        t.append("Data: ").append(registro.getInicio().format(FORMATO_DATA))
                .append("   Início: ").append(registro.getInicio().format(FORMATO_HORA))
                .append("   Fim: ").append(fim.format(FORMATO_HORA)).append('\n');
        boolean android = registro.getPlataforma() == Plataforma.ANDROID;
        t.append("Plataforma: ").append(registro.getPlataforma())
                .append(android ? " | Dispositivo: " : " | Endereço: ")
                .append(registro.getAlvo()).append('\n');
        t.append("PN: ").append(valorOuNaoIdentificado(registro.getPn())).append('\n');
        t.append("SN: ").append(valorOuNaoIdentificado(registro.getSn())).append('\n');
        t.append("Resumo: ")
                .append(registro.contar(Veredito.OK)).append(" OK, ")
                .append(registro.contar(Veredito.ALERTA)).append(" ALERTA, ")
                .append(registro.contar(Veredito.FALHA)).append(" FALHA, ")
                .append(registro.contar(Veredito.INFO)).append(" INFO\n");

        for (ExecucaoAvaliada e : registro.getExecucoes()) {
            ResultadoExecucao r = e.getResultado();
            Integer codigo = r.getCodigoSaida();

            t.append('\n');
            t.append("--- [").append(e.getHora().format(FORMATO_HORA)).append("] ")
                    .append(e.getNomeTeste()).append(" ---\n");
            t.append("Comando: ").append(r.getTextoComando()).append('\n');
            t.append("Veredito: ").append(e.getAvaliacao().getVeredito())
                    .append(" - ").append(e.getAvaliacao().getDetalhe()).append('\n');
            t.append("Código de saída: ").append(codigo == null ? "-" : codigo.toString())
                    .append(" | Duração: ").append(segundos(r.getDuracaoMs())).append(" s\n");
            t.append("Saída:\n").append(r.getSaida());
            if (!r.getSaida().endsWith("\n")) {
                t.append('\n');
            }
        }
        return t.toString();
    }

    private static String valorOuNaoIdentificado(String valor) {
        return (valor == null || valor.isEmpty()) ? RegistroTeste.NAO_IDENTIFICADO : valor;
    }

    public static String segundos(long ms) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", ms / 1000.0);
    }
}
