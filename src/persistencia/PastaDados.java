package persistencia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PastaDados {

    private static final String NOME = "ShellTool";
    private static final String NOME_ANTIGO = "BashTool";

    // <pasta do usuário>/ShellTool; se só existir a pasta antiga (BashTool), ela é renomeada
    public static Path obter() {
        Path home = Paths.get(System.getProperty("user.home"));
        Path pasta = home.resolve(NOME);
        Path antiga = home.resolve(NOME_ANTIGO);
        if (!Files.exists(pasta) && Files.isDirectory(antiga)) {
            try {
                Files.move(antiga, pasta);
            } catch (IOException e) {
                System.err.println("Não foi possível renomear " + antiga + " para " + pasta + ": " + e.getMessage());
            }
        }
        return pasta;
    }
}
