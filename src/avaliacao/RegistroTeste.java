package avaliacao;

import model.Plataforma;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// classe responsavel pelo cabeçalho basico de cada teste
public class RegistroTeste {

    public static final String NAO_IDENTIFICADO = "not identified";

    private final LocalDateTime inicio;
    private final Plataforma plataforma;
    private final String alvo;
    private String pn;
    private String sn;
    private final List<ExecucaoAvaliada> execucoes = new ArrayList<>();

    public RegistroTeste(LocalDateTime inicio, Plataforma plataforma, String alvo) {
        this.inicio = inicio;
        this.plataforma = plataforma;
        this.alvo = alvo;
    }

    public Plataforma getPlataforma() {
        return plataforma;
    }

    public String getAlvo() {
        return alvo;
    }

    public void adicionar(ExecucaoAvaliada execucao) {
        execucoes.add(execucao);
    }

    public List<ExecucaoAvaliada> getExecucoes() {
        return Collections.unmodifiableList(execucoes);
    }

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
