package conexao;

import java.io.File;

public class LocalizadorAdb {

    public static String resolver(String configurado) {
        // 1. Caminho escolhido pelo usuário (config.txt)
        if (new File(configurado).isFile()) {
            return configurado;
        }

        // 2. Android Studio (pasta padrão do SDK)
        File sdk = new File(System.getenv("LOCALAPPDATA"), "Android\\Sdk\\platform-tools\\adb.exe");
        if (sdk.isFile()) {
            return sdk.getAbsolutePath();
        }

        return "adb";
    }
}