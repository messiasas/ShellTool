package model;

/* Como uma execução de comando terminou */
public enum StatusExecucao {
    CONCLUIDO,        // o marcador de fim chegou
    TEMPO_ESGOTADO,   // passou do tempo limite sem o marcador
    CANCELADO,        // o usuário clicou em "Cancelar" (Ctrl+C)
    CONEXAO_PERDIDA   // a conexão caiu no meio da execução
}
