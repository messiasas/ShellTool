package model;

/*
 * Como o texto do teste é enviado ao terminal:
 *   SHELL -> vai direto para o shell, do jeito que está escrito
 *   AT    -> é um comando para o modem (ex.: AT+CSQ); a ferramenta monta a linha de shell
 *            que abre a porta do modem, escreve o comando e lê a resposta
 */
public enum TipoComando {
    SHELL,
    AT
}
