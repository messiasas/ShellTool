package avaliacao;

import model.Comando;
import model.ResultadoExecucao;

import java.util.List;

/*
 * Decide o veredito de uma execução, em CAMADAS. A primeira camada que decidir vence:
 *   a) status da execução (tempo esgotado, cancelado, conexão perdida)
 *   b) teste informativo -> INFO
 *   c) regras do próprio teste   (FALHA, depois ALERTA, depois OK)
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

    /* comando pode ser null (comando livre) */
    public ResultadoAvaliado avaliar(Comando comando, ResultadoExecucao resultado) {

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

        if (comando != null) {
            // b) teste informativo: só mostra dados, não tem certo/errado
            if (comando.isInformativo()) {
                return new ResultadoAvaliado(Veredito.INFO, "Concluído");
            }

            // c) regras do próprio teste
            ResultadoAvaliado peloTeste = aplicarRegras(comando.getRegras(), saida);
            if (peloTeste != null) {
                return peloTeste;
            }
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
            for (Regra regra : regras) {
                if (regra.getTipo() != tipo) {
                    continue;
                }
                String detalhe = regra.verificar(saida);
                if (detalhe != null) {
                    return new ResultadoAvaliado(tipo, detalhe);
                }
            }
        }
        return null; // nenhuma regra casou: a próxima camada decide
    }
}
