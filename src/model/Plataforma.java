package model;

/* enum é um tipo com valores fixos.
Uma plataforma pode ser somente PROLIN ou ANDROID*/
public enum Plataforma {

    PROLIN("Prolin"),
    ANDROID("Android");

    private final String nome;

    Plataforma(String nome){
        this.nome = nome;
    }

    /*toString() devolvendo o nome: mesmo truque do enum.
    Quando esse objeto for colocado numa lista do Swing, ela mostra só o nome, em vez de
     algo como model.Plataforma@1b6d3586
     */
    @Override
    public String toString(){
        return nome;
    }

}
