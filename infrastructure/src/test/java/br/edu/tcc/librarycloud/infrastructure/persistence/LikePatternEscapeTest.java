package br.edu.tcc.librarycloud.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternEscapeTest {

    @Test
    @DisplayName("texto comum não é alterado")
    void plainTextIsUnchanged() {
        assertThat(JdbcBookRepository.escapeLikePattern("Dom Casmurro")).isEqualTo("Dom Casmurro");
    }

    @Test
    @DisplayName("escapa %, _ e a própria barra invertida")
    void escapesWildcards() {
        assertThat(JdbcBookRepository.escapeLikePattern("100%")).isEqualTo("100\\%");
        assertThat(JdbcBookRepository.escapeLikePattern("a_b")).isEqualTo("a\\_b");
        assertThat(JdbcBookRepository.escapeLikePattern("a\\b")).isEqualTo("a\\\\b");
    }
}
