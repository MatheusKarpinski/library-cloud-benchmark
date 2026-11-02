package br.edu.tcc.librarycloud.contract;

import br.edu.tcc.librarycloud.application.BookData;
import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.domain.Book;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseMappingTest {

    @Test
    @DisplayName("BookResponse copia todos os campos do livro e expõe o id como texto")
    void bookResponseCopiesAllFields() {
        UUID id = UUID.randomUUID();
        Book book = Book.restore(id, "Dom Casmurro", "Machado de Assis", "9788535902775", 1899, "Romance", false);

        BookResponse response = BookResponse.from(book);

        assertThat(response.id()).isEqualTo(id.toString());
        assertThat(response.titulo()).isEqualTo("Dom Casmurro");
        assertThat(response.autor()).isEqualTo("Machado de Assis");
        assertThat(response.isbn()).isEqualTo("9788535902775");
        assertThat(response.anoPublicacao()).isEqualTo(1899);
        assertThat(response.categoria()).isEqualTo("Romance");
        assertThat(response.disponivel()).isFalse();
    }

    @Test
    @DisplayName("BookResponse mantém ISBN nulo")
    void bookResponseKeepsNullIsbn() {
        Book book = Book.create("Livro", "Autor", null, 2000, "Teste", true);

        assertThat(BookResponse.from(book).isbn()).isNull();
    }

    @Test
    @DisplayName("PageResponse converte os itens e preserva os totais")
    void pageResponseMapsItemsAndTotals() {
        Book a = Book.create("A", "Autor", null, 2000, "Teste", true);
        Book b = Book.create("B", "Autor", null, 2000, "Teste", true);
        Page<Book> page = new Page<>(List.of(a, b), 1, 2, 5L);

        PageResponse<BookResponse> response = PageResponse.from(page, BookResponse::from);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).titulo()).isEqualTo("A");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("BookRequest converte para BookData sem alterar os valores")
    void bookRequestConvertsToBookData() {
        BookRequest request = new BookRequest("T", "A", "123", 1999, "C", null);

        BookData data = request.toBookData();

        assertThat(data.titulo()).isEqualTo("T");
        assertThat(data.anoPublicacao()).isEqualTo(1999);
        assertThat(data.disponivel()).isNull();
    }

    @Test
    @DisplayName("ReadinessResponse reflete a disponibilidade do banco")
    void readinessReflectsDatabase() {
        assertThat(ReadinessResponse.of(true).up()).isTrue();
        assertThat(ReadinessResponse.of(false).up()).isFalse();
        assertThat(ReadinessResponse.of(false).database()).isEqualTo("DOWN");
    }
}
