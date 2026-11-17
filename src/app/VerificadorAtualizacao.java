package app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import app.Versao;

public class VerificadorAtualizacao {

    private static final String URL_RELEASE =
            "https://api.github.com/repos/messiasas/ShellTool/releases/latest";

    // Retorna a atualização (versão + link do .exe) ou null se não houver
    public static Atualizacao buscarAtualizacao() {
        try {
            HttpClient cliente = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL_RELEASE))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resposta = cliente.send(req, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() != 200) {
                return null;
            }
            String json = resposta.body();

            Matcher mVersao = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").matcher(json);
            Matcher mUrl = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"").matcher(json);

            if (mVersao.find() && mUrl.find()) {
                String remota = mVersao.group(1);
                if (compararVersoes(remota, Versao.ATUAL) > 0) {
                    return new Atualizacao(remota, mUrl.group(1));
                }
            }
        } catch (Exception e) {
            System.err.println("Falha ao verificar atualização: " + e.getMessage());
        }
        return null;
    }

    // Baixa o instalador para a pasta temporária e devolve o caminho
    public static Path baixarInstalador(Atualizacao atualizacao) throws IOException, InterruptedException {
        HttpClient cliente = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(atualizacao.urlInstalador())).build();

        Path destino = Path.of(System.getProperty("java.io.tmpdir"),
                "ShellTool-" + atualizacao.versao() + ".exe");
        HttpResponse<Path> resposta = cliente.send(req, HttpResponse.BodyHandlers.ofFile(destino));
        if (resposta.statusCode() != 200) {
            throw new IOException("Download falhou: HTTP " + resposta.statusCode());
        }
        return destino;
    }

    // > 0 se a for maior que b, 0 se iguais, < 0 se menor
    static int compararVersoes(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int tamanho = Math.max(pa.length, pb.length);
        for (int i = 0; i < tamanho; i++) {
            int na = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int nb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (na != nb) {
                return na - nb;
            }
        }
        return 0;
    }
}