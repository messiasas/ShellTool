package avaliacao;

import model.Comando;
import model.ResultadoExecucao;
import model.TipoComando;

import java.util.List;

// Camadas, a primeira que decidir vence: status -> informativo -> regras do teste -> regras gerais -> código de saída
public class AvaliadorResultado {

    private static final List<Regra> REGRAS_GERAIS = List.of(
            new Regra(Veredito.FALHA, "Network is unreachable", "Sem rota", false),
            new Regra(Veredito.FALHA, "\\b100% packet loss", "Sem resposta", true),
            new Regra(Veredito.FALHA, "bad address", "Falha de DNS", false),

            // Antes de "not found": a primeira FALHA que casar vence, então o mais específico vem primeiro
            new Regra(Veredito.FALHA, "unknown host", "Falha de DNS", false),
            new Regra(Veredito.FALHA, "device ('[^']*' )?not found|no devices/emulators found",
                    "Dispositivo não encontrado", true),
            new Regra(Veredito.FALHA, "device offline", "Dispositivo offline", false),
            new Regra(Veredito.FALHA, "unauthorized", "Depuração USB não autorizada", false),
            new Regra(Veredito.FALHA, "inaccessible or not found", "Comando inexistente", false),
            new Regra(Veredito.FALHA, "not found","Comando ou arquivo inexistente", false),
            new Regra(Veredito.FALHA, "No such file or directory", "Arquivo inexistente", false),
            new Regra(Veredito.FALHA, "Permission denied", "Permissão negada", false),
            new Regra(Veredito.ALERTA, "\\b[1-9][0-9]?% packet loss", "Perda parcial de pacotes", true)
    );

    // FALHA antes de OK: um OK nunca pode esconder uma falha
    private static final Veredito[] ORDEM = {Veredito.FALHA, Veredito.ALERTA, Veredito.OK};

    public ResultadoAvaliado avaliar(Comando comando, ResultadoExecucao resultado) {
        TipoComando tipo = (comando == null) ? TipoComando.SHELL : comando.getTipo();
        return avaliar(comando, tipo, resultado);
    }

    public ResultadoAvaliado avaliar(Comando comando, TipoComando tipo, ResultadoExecucao resultado) {

        switch (resultado.getStatus()) {
            case TEMPO_ESGOTADO:
                return new ResultadoAvaliado(Veredito.ALERTA, "Tempo esgotado");
            case CANCELADO:
                return new ResultadoAvaliado(Veredito.INFO, "Cancelado");
            case CONEXAO_PERDIDA:
                return new ResultadoAvaliado(Veredito.FALHA, "Conexão perdida");
            default:
                break;
        }

        String saida = resultado.getSaida();

        if (comando != null && comando.isInformativo()) {
            return new ResultadoAvaliado(Veredito.INFO, "Concluído");
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
            return new ResultadoAvaliado(Veredito.OK, "Modem respondeu OK");
        }

        ResultadoAvaliado pelasGerais = aplicarRegras(REGRAS_GERAIS, saida);
        if (pelasGerais != null) {
            return pelasGerais;
        }

        Integer codigo = resultado.getCodigoSaida();
        if (codigo == null) {
            return new ResultadoAvaliado(Veredito.INFO, "Sem código de saída");
        }
        if (codigo == 0) {
            return new ResultadoAvaliado(Veredito.OK, "Executado (sem regra)");
        }
        return new ResultadoAvaliado(Veredito.FALHA, "Código de saída " + codigo);
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
                return new ResultadoAvaliado(Veredito.FALHA, "Erro do modem: " + l);
            }
            if (l.equals("OK")) {
                temOk = true;
            }
        }
        if (!temOk) {
            return new ResultadoAvaliado(Veredito.ALERTA, "Modem não respondeu");
        }
        return null;
    }
}
