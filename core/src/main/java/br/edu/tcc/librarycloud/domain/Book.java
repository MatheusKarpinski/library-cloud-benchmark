package br.edu.tcc.librarycloud.domain;

import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entidade Livro. Imutável: qualquer alteração gera uma nova instância com o mesmo id.
 *
 * <p>Todas as regras de formato e limites ficam AQUI, em um único lugar, e são reutilizadas
 * pelas duas implantações (Container Apps e Functions). Os limites de tamanho devem ser
 * espelhados nas colunas do banco (ver docs/architecture-decisions.md, ADR-010).
 */
public final class Book {

    public static final int TITULO_MAX = 200;
    public static final int AUTOR_MAX = 150;
    public static final int CATEGORIA_MAX = 80;
    public static final int ANO_MIN = 1000;

    /** ISBN já normalizado: 10 caracteres (o último pode ser X) ou 13 dígitos. */
    private static final Pattern ISBN_FORMAT = Pattern.compile("^(\\d{9}[\\dX]|\\d{13})$");

    private final UUID id;
    private final String titulo;
    private final String autor;
    private final String isbn;
    private final int anoPublicacao;
    private final String categoria;
    private final boolean disponivel;

    private Book(UUID id, String titulo, String autor, String isbn,
                 int anoPublicacao, String categoria, boolean disponivel) {
        this.id = id;
        this.titulo = titulo;
        this.autor = autor;
        this.isbn = isbn;
        this.anoPublicacao = anoPublicacao;
        this.categoria = categoria;
        this.disponivel = disponivel;
    }

    /**
     * Cria um livro novo, gerando o identificador na própria aplicação.
     * {@code disponivel} nulo é interpretado como {@code true}.
     */
    public static Book create(String titulo, String autor, String isbn,
                              Integer anoPublicacao, String categoria, Boolean disponivel) {
        return build(UUID.randomUUID(), titulo, autor, isbn, anoPublicacao, categoria, disponivel);
    }

    /** Reconstitui um livro já existente (uso da camada de persistência). */
    public static Book restore(UUID id, String titulo, String autor, String isbn,
                               Integer anoPublicacao, String categoria, Boolean disponivel) {
        Objects.requireNonNull(id, "id");
        return build(id, titulo, autor, isbn, anoPublicacao, categoria, disponivel);
    }

    /** Devolve uma nova versão do livro com os dados informados, preservando o id. */
    public Book withDetails(String titulo, String autor, String isbn,
                            Integer anoPublicacao, String categoria, Boolean disponivel) {
        return build(this.id, titulo, autor, isbn, anoPublicacao, categoria, disponivel);
    }

    private static Book build(UUID id, String titulo, String autor, String isbn,
                              Integer anoPublicacao, String categoria, Boolean disponivel) {
        List<FieldViolation> violations = new ArrayList<>();

        String cleanTitulo = clean(titulo);
        requireText(violations, "titulo", cleanTitulo, TITULO_MAX);

        String cleanAutor = clean(autor);
        requireText(violations, "autor", cleanAutor, AUTOR_MAX);

        String cleanCategoria = clean(categoria);
        requireText(violations, "categoria", cleanCategoria, CATEGORIA_MAX);

        String normalizedIsbn = normalizeIsbn(isbn);
        if (normalizedIsbn != null && !ISBN_FORMAT.matcher(normalizedIsbn).matches()) {
            violations.add(new FieldViolation("isbn",
                    "formato inválido (use ISBN-10 ou ISBN-13, com ou sem hífens)"));
        }

        int maxYear = Year.now().getValue() + 1;
        if (anoPublicacao == null) {
            violations.add(new FieldViolation("anoPublicacao", "é obrigatório"));
        } else if (anoPublicacao < ANO_MIN || anoPublicacao > maxYear) {
            violations.add(new FieldViolation("anoPublicacao",
                    "deve estar entre " + ANO_MIN + " e " + maxYear));
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        return new Book(id, cleanTitulo, cleanAutor, normalizedIsbn,
                anoPublicacao, cleanCategoria, disponivel == null || disponivel);
    }

    private static void requireText(List<FieldViolation> violations, String field,
                                    String value, int maxLength) {
        if (value == null) {
            violations.add(new FieldViolation(field, "é obrigatório"));
        } else if (value.length() > maxLength) {
            violations.add(new FieldViolation(field,
                    "deve ter no máximo " + maxLength + " caracteres"));
        }
    }

    /** Remove espaços nas pontas; texto vazio vira null. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** Remove hífens e espaços e coloca em maiúsculas; vazio vira null. */
    private static String normalizeIsbn(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null) {
            return null;
        }
        String normalized = cleaned.replaceAll("[\\s-]", "").toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    public UUID id() { return id; }
    public String titulo() { return titulo; }
    public String autor() { return autor; }
    /** ISBN normalizado (sem hífens) ou {@code null} quando não informado. */
    public String isbn() { return isbn; }
    public int anoPublicacao() { return anoPublicacao; }
    public String categoria() { return categoria; }
    public boolean disponivel() { return disponivel; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Book book && id.equals(book.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Book[id=" + id + ", titulo=" + titulo + "]";
    }
}
