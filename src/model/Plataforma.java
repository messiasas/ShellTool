package model;

public enum Plataforma {

    PROLIN("Prolin"),
    ANDROID("Android");

    private final String nome;

    Plataforma(String nome){
        this.nome = nome;
    }

    @Override
    public String toString(){
        return nome;
    }

}
