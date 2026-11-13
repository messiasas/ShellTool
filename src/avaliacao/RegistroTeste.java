package avaliacao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Uma sessão de "Registrar teste": guarda início, PN, SN e todas as execuções
 * concluídas enquanto o registro estiver ativo.
 */
public class RegistroTeste {

    public static final String NAO_IDENTIFICADO = "não identificado";

    private final LocalDateTime inicio;
    private String pn; // null enquanto ainda não foi lido
    private String sn;
    private final List<ExecucaoAvaliada> execucoes = new ArrayList<>();

    public RegistroTeste(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public void adicionar(ExecucaoAvaliada execucao) {
        execucoes.add(execucao);
    }

    /* Lista só para leitura: quem chama não consegue alterar a lista interna */
    public List<ExecucaoAvaliada> getExecucoes() {
        return Collections.unmodifiableList(execucoes);
    }

    /* Quantas execuções tiveram este veredito (usado no "Resumo") */
    public int contar(Veredito veredito) {
        int total = 0;
        for (ExecucaoAvaliada e : execucoes) {
            if (e.getAvaliacao().getVeredito() == veredito) {
                total++;
            }
        }
        return total;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public String getPn() {
        return pn;
    }

    public void setPn(String pn) {
        this.pn = pn;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }
}
