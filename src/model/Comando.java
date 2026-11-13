package model;

import avaliacao.Regra;

import java.util.ArrayList;
import java.util.List;

public class Comando {

    private String nome;
    private Plataforma plataforma;
    private String txtComando;
    private String descricao;

    // Regras que decidem OK/ALERTA/FALHA a partir da saída (começa vazia)
    private List<Regra> regras = new ArrayList<>();
    // Teste informativo: só mostra dados, o veredito é sempre INFO
    private boolean informativo = false;

    // Espera padrão (segundos) pela resposta do modem nos testes AT
    public static final int ESPERA_PADRAO = 2;

    // SHELL = comando normal; AT = txtComando guarda só o comando AT (ex.: AT+CSQ)
    private TipoComando tipo = TipoComando.SHELL;
    // Só usado no tipo AT: quanto tempo esperar a resposta do modem
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
        // guarda uma cópia, para a lista não ser alterada "por fora" sem querer
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
        // null vira SHELL, para nunca ficar sem tipo
        this.tipo = (tipo == null) ? TipoComando.SHELL : tipo;
    }

    public boolean isAt() {
        return tipo == TipoComando.AT;
    }

    public Integer getEsperaSegundos() {
        return esperaSegundos;
    }

    public void setEsperaSegundos(Integer esperaSegundos) {
        // valor ausente ou menor que 1 volta para o padrão
        this.esperaSegundos = (esperaSegundos == null || esperaSegundos < 1) ? ESPERA_PADRAO : esperaSegundos;
    }

    /*
    * toString() devolvendo o nome: mesmo truque do enum. Quando esse objeto for
    * colocado numa lista do Swing, ela mostra só o nome, em vez de algo como model.Comando@1b6d3586.
    */
    @Override
    public String toString() {
        return nome;
    }



}


