package persistencia;

import java.nio.file.Path;
import java.nio.file.Paths;

// devolve somente o caminho da pasta do user
public class PastaDados {

    private static final String NOME = "ShellTool";

    public static Path obter() {
        return Paths.get(System.getProperty("user.home")).resolve(NOME);
    }
}
