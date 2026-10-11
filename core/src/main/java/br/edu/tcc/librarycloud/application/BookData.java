package br.edu.tcc.librarycloud.application;

/**
 * Dados de entrada para cadastrar ou atualizar um livro. Os tipos são "caixa alta"
 * (Integer, Boolean) de propósito: assim o núcleo consegue distinguir "campo ausente" de
 * "campo com valor" e produzir a mensagem de validação correta, igual nas duas implantações.
 */
public record BookData(
        String titulo,
        String autor,
        String isbn,
        Integer anoPublicacao,
        String categoria,
        Boolean disponivel) {
}
