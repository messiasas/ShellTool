package avaliacao;

import model.Comando;
import model.ResultadoExecucao;
import model.TipoComando;

import java.util.List;

/*
 * Decide o veredito de uma execução, em CAMADAS. A primeira camada que decidir vence:
 *   a) status da execução (tempo esgotado, cancelado, conexão perdida)
 *   b) teste informativo -> INFO
 *   c) regras do próprio teste   (FALHA, FALHA_SEM, [checagens do modem se for AT], ALERTA, OK)
 *   d) regras gerais embutidas   (FALHA, depois ALERTA)
 *   e) código de saída ($?)
 * Assim, o mais específico (o teste) fala antes do genérico (regras gerais e código de saída).
 */
public class AvaliadorResultado {

    /*
     * Regras gerais: valem para qualquer comando, inclusive o comando livre.
     * List.of cria uma lista imutável (não dá para adicionar/remover depois).
     */
    private static final List<Regra> REGRAS_GERAIS = List.of(
            new Regra(Veredito.FALHA, "Network is unreachable", "Sem rota", false),
            new Regra(Veredito.FALHA, "\\b100% packet loss", "Sem resposta", true),
            new Regra(Veredito.FALHA, "bad address", "Falha de DNS", false),
            new Regra(Veredito.FALHA, "not found", "Comando ou arquivo inexistente", false),
            new Regra(Veredito.FALHA, "No such file or directory", "Arquivo inexistente", false),
            new Regra(Veredito.FALHA, "Permission denied", "Permissão negada", false),
            new Regra(Veredito.ALERTA, "\\b[1-9][0-9]?% packet loss", "Perda parcial de pacotes", true)
    );

    /*
     * Ordem de verificação dos tipos. FALHA vem antes de OK porque uma mesma saída
     * pode casar com as duas: ex.: um ping que perdeu tudo numa interface e respondeu
     * em outra. Na dúvida, o problema precisa aparecer; um OK nunca pode esconder uma falha.
     */
    private static final Veredito[] ORDEM = {Veredito.FALHA, Veredito.ALERTA, Veredito.OK};

    /* comando pode ser null (comando livre); o tipo vem do próprio teste */
    public ResultadoAvaliado avaliar(Comando comando, ResultadoExecucao resultado) {
        TipoComando tipo = (comando == null) ? TipoComando.SHELL : comando.getTipo();
        return avaliar(comando, tipo, resultado);
    }

    /*
     * Versão que recebe o tipo separado: o comando livre não tem um Comando salvo,
     * mas pode ser AT (caixa "AT" marcada) e então também passa pelas checagens do modem.
     */
    public ResultadoAvaliado avaliar(Comando comando, TipoComando tipo, ResultadoExecucao resultado) {

        // a) status: se não terminou normalmente, nem olha a saída
        switch (resultado.getStatus()) {
            case TEMPO_ESGOTADO:
                return new ResultadoAvaliado(Veredito.ALERTA, "Tempo esgotado");
            case CANCELADO:
                return new ResultadoAvaliado(Veredito.INFO, "Cancelado");
            case CONEXAO_PERDIDA:
                return new ResultadoAvaliado(Veredito.FALHA, "Conexão perdida");
            default:
                break; // CONCLUIDO: segue para as próximas camadas
        }

        String saida = resultado.getSaida();

        // b) teste informativo: só mostra dados, não tem certo/errado
        if (comando != null && comando.isInformativo()) {
            return new ResultadoAvaliado(Veredito.INFO, "Concluído");
        }

        // c) regras do próprio teste (lista vazia no comando livre)
        List<Regra> regras = (comando == null) ? List.of() : comando.getRegras();

        // c1) falha e falha_sem
        ResultadoAvaliado r = aplicarRegras(regras, Veredito.FALHA, false, saida);
        if (r == null) {
            r = aplicarRegras(regras, Veredito.FALHA, true, saida);
        }
        if (r != null) {
            return r;
        }

        // c2) teste AT: o modem respondeu ERROR ou nem respondeu?
        boolean ehAt = (tipo == TipoComando.AT);
        if (ehAt) {
            ResultadoAvaliado peloModem = verificarRespostaModem(saida);
            if (peloModem != null) {
                return peloModem;
            }
        }

        // c3) alerta e ok
        r = aplicarRegras(regras, Veredito.ALERTA, false, saida);
        if (r == null) {
            r = aplicarRegras(regras, Veredito.OK, false, saida);
        }
        if (r != null) {
            return r;
        }

        // c4) AT sem regra que case: chegou aqui, então há uma linha "OK"
        if (ehAt) {
            return new ResultadoAvaliado(Veredito.OK, "Modem respondeu OK");
        }

        // d) regras gerais
        ResultadoAvaliado pelasGerais = aplicarRegras(REGRAS_GERAIS, saida);
        if (pelasGerais != null) {
            return pelasGerais;
        }

        // e) código de saída
        Integer codigo = resultado.getCodigoSaida();
        if (codigo == null) {
            return new ResultadoAvaliado(Veredito.INFO, "Sem código de saída");
        }
        if (codigo == 0) {
            return new ResultadoAvaliado(Veredito.OK, "Executado (sem regra)");
        }
        return new ResultadoAvaliado(Veredito.FALHA, "Código de saída " + codigo);
    }

    /* Percorre as regras na ordem FALHA -> ALERTA -> OK; devolve a primeira que casar */
    private ResultadoAvaliado aplicarRegras(List<Regra> regras, String saida) {
        for (Veredito tipo : ORDEM) {
            ResultadoAvaliado r = aplicarRegras(regras, tipo, false, saida);
            if (r != null) {
                return r;
            }
        }
        return null; // nenhuma regra casou: a próxima camada decide
    }

    /*
     * Só as regras de um tipo (e de ausência ou não), na ordem do arquivo.
     * A primeira que casar vence.
     */
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

    /*
     * Olha a resposta do modem LINHA A LINHA, comparando a linha inteira (ou o começo dela).
     * Assim nunca casa com o eco da linha de shell, que tem o comando dentro de echo -e "...".
     * Devolve null se o modem respondeu OK sem erro (as regras alerta/ok decidem depois).
     */
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
