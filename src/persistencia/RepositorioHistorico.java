package persistencia;

import avaliacao.ExecucaoAvaliada;
import avaliacao.RegistroTeste;
import avaliacao.Veredito;
import model.ResultadoExecucao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*
 * Grava os registros de teste em <pasta do usuário>/BashTool/historico/.
 * A tela decide o nome e se sobrescreve; aqui só ficam caminho e conteúdo do arquivo.
 */
public class RepositorioHistorico {

    // DateTimeFormatter converte data/hora em texto no formato pedido
    private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path pasta;

    public RepositorioHistorico() {
        pasta = Paths.get(System.getProperty("user.home"), "BashTool", "historico");
    }

    /* Tira do nome os caracteres que o Windows não aceita em arquivos: \ / : * ? " < > | */
    public static String limparNome(String nome) {
        return nome.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    /* Ex.: "Vivo LTE" iniciado em 13/11/2026 -> .../historico/Vivo LTE-13-11-26.txt */
    public Path caminhoPara(String nomeLimpo, LocalDateTime inicio) {
        return pasta.resolve(nomeLimpo + "-" + inicio.format(FORMATO_NOME) + ".txt");
    }

    /* Se o arquivo já existe, procura "nome (2).txt", "nome (3).txt"... até achar um livre */
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
        // \r\n: quebra de linha do Windows, para o Bloco de Notas mostrar certinho
        conteudo = conteudo.replace("\n", "\r\n");
        Files.writeString(caminho, conteudo, StandardCharsets.UTF_8);
    }

    private String montarConteudo(RegistroTeste registro, String nomeTeste, LocalDateTime fim) {
        StringBuilder t = new StringBuilder();
        t.append("=== Registro de teste: ").append(nomeTeste).append(" ===\n");
        t.append("Data: ").append(registro.getInicio().format(FORMATO_DATA))
                .append("   Início: ").append(registro.getInicio().format(FORMATO_HORA))
                .append("   Fim: ").append(fim.format(FORMATO_HORA)).append('\n');
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

    /* 2100 ms -> "2,1" (vírgula decimal do português) */
    public static String segundos(long ms) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", ms / 1000.0);
    }
}
