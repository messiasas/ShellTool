package model;

public class Comando {

    private String nome;
    private Plataforma plataforma;
    private String txtComando;
    private String descricao;

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

    /*
    * toString() devolvendo o nome: mesmo truque do enum. Quando esse objeto for
    * colocado numa lista do Swing, ela mostra só o nome, em vez de algo como model.Comando@1b6d3586.
    */
    @Override
    public String toString() {
        return nome;
    }



}


