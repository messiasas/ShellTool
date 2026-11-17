package model;

import avaliacao.Regra;

import java.util.ArrayList;
import java.util.List;

public class Comando {

    private String nome;
    private Plataforma plataforma;
    private String txtComando;
    private String descricao;

    private List<Regra> regras = new ArrayList<>();
    private boolean informativo = false;

    public static final int ESPERA_PADRAO = 2;

    private TipoComando tipo = TipoComando.SHELL;

    private Integer esperaSegundos = ESPERA_PADRAO;

    public Comando(String nome, Plataforma plataforma, String textoComando, String descricao) {
        this.nome = nome;
        this.plataforma = plataforma;
        this.txtComando = textoComando;
        this.descricao = descricao;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Plataforma getPlataforma() {
        return plataforma;
    }

    public String getTxtComando() {
        return txtComando;
    }

    public String getDescricao() {
        return descricao;
    }

    public List<Regra> getRegras() {
        return regras;
    }

    public void setRegras(List<Regra> regras) {
        this.regras = new ArrayList<>(regras);
    }

    public void adicionarRegra(Regra regra) {
        regras.add(regra);
    }

    public boolean isInformativo() {
        return informativo;
    }

    public void setInformativo(boolean informativo) {
        this.informativo = informativo;
    }

    public TipoComando getTipo() {
        return tipo;
    }

    public void setTipo(TipoComando tipo) {
        this.tipo = (tipo == null) ? TipoComando.SHELL : tipo;
    }

    public boolean isAt() {
        return tipo == TipoComando.AT;
    }

    public Integer getEsperaSegundos() {
        return esperaSegundos;
    }

    public void setEsperaSegundos(Integer esperaSegundos) {
        this.esperaSegundos = (esperaSegundos == null || esperaSegundos < 1) ? ESPERA_PADRAO : esperaSegundos;
    }

    @Override
    public String toString() {
        return nome;
    }

}
