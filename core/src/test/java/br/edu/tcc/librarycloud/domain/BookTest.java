package br.edu.tcc.librarycloud.domain;

import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookTest {

    private static List<String> invalidFields(ValidationException ex) {
        return ex.violations().stream().map(FieldViolation::field).toList();
    }

    @Test
    @DisplayName("cria livro válido e gera um id")
    void createsValidBookAndGeneratesId() {
        Book book = Book.create("Dom Casmurro", "Machado de Assis", "9788535902775", 1899, "Romance", true);

        assertThat(book.id()).isNotNull();
        assertThat(book.titulo()).isEqualTo("Dom Casmurro");
        assertThat(book.autor()).isEqualTo("Machado de Assis");
        assertThat(book.isbn()).isEqualTo("9788535902775");
        assertThat(book.anoPublicacao()).isEqualTo(1899);
        assertThat(book.categoria()).isEqualTo("Romance");
        assertThat(book.disponivel()).isTrue();
    }

    @Test
    @DisplayName("remove espaços nas pontas e normaliza o ISBN (sem hífens, maiúsculas)")
    void trimsTextAndNormalizesIsbn() {
        Book book = Book.create("  Dom Casmurro  ", " Machado de Assis ", " 978-85-359-0277-5 ", 1899, " Romance ", true);

        assertThat(book.titulo()).isEqualTo("Dom Casmurro");
        assertThat(book.autor()).isEqualTo("Machado de Assis");
        assertThat(book.categoria()).isEqualTo("Romance");
        assertThat(book.isbn()).isEqualTo("9788535902775");
    }

    @Test
    @DisplayName("aceita ISBN-10 terminado em X (minúsculo ou maiúsculo)")
    void acceptsIsbn10WithCheckLetterX() {
        Book book = Book.create("Livro", "Autor", "0-8044-2957-x", 1990, "Teste", true);

        assertThat(book.isbn()).isEqualTo("080442957X");
    }

    @Test
    @DisplayName("ISBN é opcional: nulo ou em branco vira null")
    void isbnIsOptional() {
        assertThat(Book.create("Livro", "Autor", null, 2000, "Teste", true).isbn()).isNull();
        assertThat(Book.create("Livro", "Autor", "   ", 2000, "Teste", true).isbn()).isNull();
    }

    @Test
    @DisplayName("disponivel ausente é interpretado como verdadeiro")
    void missingDisponivelDefaultsToTrue() {
        Book book = Book.create("Livro", "Autor", null, 2000, "Teste", null);

        assertThat(book.disponivel()).isTrue();
    }

    @Test
    @DisplayName("reúne todas as violações de campos obrigatórios de uma só vez")
    void collectsAllRequiredFieldViolations() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Book.create("   ", null, null, null, "", true));

        assertThat(invalidFields(ex)).containsExactlyInAnyOrder("titulo", "autor", "categoria", "anoPublicacao");
    }

    @Test
    @DisplayName("rejeita título maior que o limite")
    void rejectsTooLongTitle() {
        String tooLong = "x".repeat(Book.TITULO_MAX + 1);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> Book.create(tooLong, "Autor", null, 2000, "Teste", true));

        assertThat(invalidFields(ex)).containsExactly("titulo");
    }

    @Test
    @DisplayName("aceita título exatamente no limite")
    void acceptsTitleAtLimit() {
        Book book = Book.create("x".repeat(Book.TITULO_MAX), "Autor", null, 2000, "Teste", true);

        assertThat(book.titulo().length()).isEqualTo(Book.TITULO_MAX);
    }

    @Test
    @DisplayName("rejeita ISBN com formato inválido")
    void rejectsInvalidIsbn() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Book.create("Livro", "Autor", "123", 2000, "Teste", true));

        assertThat(invalidFields(ex)).containsExactly("isbn");
    }

    @Test
    @DisplayName("rejeita ano fora do intervalo permitido")
    void rejectsYearOutOfRange() {
        int tooFarInFuture = Year.now().getValue() + 2;

        ValidationException tooOld = assertThrows(ValidationException.class,
                () -> Book.create("Livro", "Autor", null, Book.ANO_MIN - 1, "Teste", true));
        ValidationException tooNew = assertThrows(ValidationException.class,
                () -> Book.create("Livro", "Autor", null, tooFarInFuture, "Teste", true));

        assertThat(invalidFields(tooOld)).containsExactly("anoPublicacao");
        assertThat(invalidFields(tooNew)).containsExactly("anoPublicacao");
    }

    @Test
    @DisplayName("withDetails preserva o id e aplica as mesmas validações")
    void withDetailsKeepsIdAndValidates() {
        Book original = Book.create("Livro", "Autor", null, 2000, "Teste", true);

        Book changed = original.withDetails("Novo título", "Outro autor", null, 2001, "Outra", false);

        assertThat(changed.id()).isEqualTo(original.id());
        assertThat(changed.titulo()).isEqualTo("Novo título");
        assertThat(changed.disponivel()).isFalse();
        assertThrows(ValidationException.class,
                () -> original.withDetails("", "Outro autor", null, 2001, "Outra", false));
    }

    @Test
    @DisplayName("igualdade considera apenas o id")
    void equalityIsBasedOnId() {
        UUID id = UUID.randomUUID();
        Book a = Book.restore(id, "Livro A", "Autor", null, 2000, "Teste", true);
        Book b = Book.restore(id, "Livro B", "Outro", null, 2001, "Outra", false);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
