package br.edu.tcc.librarycloud.application;

import java.util.List;

/**
 * Resultado paginado.
 *
 * @param items         itens da página atual
 * @param page          número da página (começa em 0)
 * @param size          tamanho solicitado da página
 * @param totalElements total de itens que satisfazem a consulta
 */
public record Page<T>(List<T> items, int page, int size, long totalElements) {

    public Page {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
