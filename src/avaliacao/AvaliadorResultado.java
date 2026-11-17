package avaliacao;

import model.Comando;
import model.ResultadoExecucao;
import model.TipoComando;

import java.util.List;

public class AvaliadorResultado {

    private static final List<Regra> REGRAS_GERAIS = List.of(
            new Regra(Veredito.FALHA, "Network is unreachable", "No route", false),
            new Regra(Veredito.FALHA, "\\b100% packet loss", "No response", true),
            new Regra(Veredito.FALHA, "bad address", "DNS failure", false),

            new Regra(Veredito.FALHA, "unknown host", "DNS failure", false),
            new Regra(Veredito.FALHA, "device ('[^']*' )?not found|no devices/emulators found",
                    "Device not found", true),
            new Regra(Veredito.FALHA, "device offline", "Device offline", false),
            new Regra(Veredito.FALHA, "unauthorized", "USB debugging not authorized", false),
            new Regra(Veredito.FALHA, "inaccessible or not found", "Command not found", false),
            new Regra(Veredito.FALHA, "not found","Command or file not found", false),
            new Regra(Veredito.FALHA, "No such file or directory", "File not found", false),
            new Regra(Veredito.FALHA, "Permission denied", "Permission denied", false),
            new Regra(Veredito.ALERTA, "\\b[1-9][0-9]?% packet loss", "Partial packet loss", true)
    );

    private static final Veredito[] ORDEM = {Veredito.FALHA, Veredito.ALERTA, Veredito.OK};

    public ResultadoAvaliado avaliar(Comando comando, ResultadoExecucao resultado) {
        TipoComando tipo = (comando == null) ? TipoComando.SHELL : comando.getTipo();
        return avaliar(comando, tipo, resultado);
    }

    public ResultadoAvaliado avaliar(Comando comando, TipoComando tipo, ResultadoExecucao resultado) {

        switch (resultado.getStatus()) {
            case TEMPO_ESGOTADO:
                return new ResultadoAvaliado(Veredito.ALERTA, "Timed out");
            case CANCELADO:
                return new ResultadoAvaliado(Veredito.INFO, "Cancelled");
            case CONEXAO_PERDIDA:
                return new ResultadoAvaliado(Veredito.FALHA, "Connection lost");
            default:
                break;
        }

        String saida = resultado.getSaida();

        if (comando != null && comando.isInformativo()) {
            return new ResultadoAvaliado(Veredito.INFO, "Done");
        }

        List<Regra> regras = (comando == null) ? List.of() : comando.getRegras();

        ResultadoAvaliado r = aplicarRegras(regras, Veredito.FALHA, false, saida);
        if (r == null) {
            r = aplicarRegras(regras, Veredito.FALHA, true, saida);
        }
        if (r != null) {
            return r;
        }

        boolean eAt = (tipo == TipoComando.AT);
        if (eAt) {
            ResultadoAvaliado peloModem = verificarRespostaModem(saida);
            if (peloModem != null) {
                return peloModem;
            }
        }

        r = aplicarRegras(regras, Veredito.ALERTA, false, saida);
        if (r == null) {
            r = aplicarRegras(regras, Veredito.OK, false, saida);
        }
        if (r != null) {
            return r;
        }

        if (eAt) {
            return new ResultadoAvaliado(Veredito.OK, "Modem replied OK");
        }

        ResultadoAvaliado pelasGerais = aplicarRegras(REGRAS_GERAIS, saida);
        if (pelasGerais != null) {
            return pelasGerais;
        }

        Integer codigo = resultado.getCodigoSaida();
        if (codigo == null) {
            return new ResultadoAvaliado(Veredito.INFO, "No exit code");
        }
        if (codigo == 0) {
            return new ResultadoAvaliado(Veredito.OK, "Executed (no rule)");
        }
        return new ResultadoAvaliado(Veredito.FALHA, "Exit code " + codigo);
    }

    private ResultadoAvaliado aplicarRegras(List<Regra> regras, String saida) {
        for (Veredito tipo : ORDEM) {
            ResultadoAvaliado r = aplicarRegras(regras, tipo, false, saida);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private ResultadoAvaliado aplicarRegras(List<Regra> regras, Veredito tipo, boolean ausencia, String saida) {
        for (Regra regra : regras) {
            if (regra.getTipo() != tipo || regra.isAusencia() != ausencia) {
                continue;
            }
            String detalhe = regra.verificar(saida);
            if (detalhe != null) {
                return new ResultadoAvaliado(tipo, detalhe);
            }
        }
        return null;
    }

    private ResultadoAvaliado verificarRespostaModem(String saida) {
        boolean temOk = false;
        for (String linha : saida.split("\n")) {
            String l = linha.trim();
            if (l.equals("ERROR") || l.startsWith("+CME ERROR") || l.startsWith("+CMS ERROR")) {
                return new ResultadoAvaliado(Veredito.FALHA, "Modem error: " + l);
            }
            if (l.equals("OK")) {
                temOk = true;
            }
        }
        if (!temOk) {
            return new ResultadoAvaliado(Veredito.ALERTA, "Modem did not respond");
        }
        return null;
    }
}
