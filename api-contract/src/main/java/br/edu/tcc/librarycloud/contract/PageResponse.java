package br.edu.tcc.librarycloud.contract;

import br.edu.tcc.librarycloud.application.Page;

import java.util.List;
import java.util.function.Function;

/** Resposta paginada das listagens e da pesquisa. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.items().stream().map(mapper).toList();
        return new PageResponse<>(mapped, page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
