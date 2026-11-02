package br.edu.tcc.librarycloud.contract;

/** Par (status HTTP, corpo) que cada adaptador converte na resposta do seu framework. */
public record ApiError(int status, ErrorResponse body) {
}
