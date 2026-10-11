package br.edu.tcc.librarycloud.application;

import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestTest {

    @Test
    @DisplayName("usa página 0 e tamanho padrão quando nada é informado")
    void appliesDefaults() {
        PageRequest request = PageRequest.of(null, null);

        assertThat(request.page()).isEqualTo(PageRequest.DEFAULT_PAGE);
        assertThat(request.size()).isEqualTo(PageRequest.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("calcula o deslocamento (offset) a partir de página e tamanho")
    void computesOffset() {
        assertThat(PageRequest.of(3, 25).offset()).isEqualTo(75L);
    }

    @Test
    @DisplayName("aceita os limites de tamanho (1 e máximo)")
    void acceptsSizeLimits() {
        assertThat(PageRequest.of(0, 1).size()).isEqualTo(1);
        assertThat(PageRequest.of(0, PageRequest.MAX_SIZE).size()).isEqualTo(PageRequest.MAX_SIZE);
    }

    @Test
    @DisplayName("rejeita página negativa e tamanhos fora do intervalo")
    void rejectsInvalidValues() {
        ValidationException negativePage = assertThrows(ValidationException.class, () -> PageRequest.of(-1, 10));
        ValidationException zeroSize = assertThrows(ValidationException.class, () -> PageRequest.of(0, 0));
        ValidationException hugeSize = assertThrows(ValidationException.class,
                () -> PageRequest.of(0, PageRequest.MAX_SIZE + 1));

        assertThat(negativePage.violations().stream().map(FieldViolation::field).toList()).containsExactly("page");
        assertThat(zeroSize.violations().stream().map(FieldViolation::field).toList()).containsExactly("size");
        assertThat(hugeSize.violations().stream().map(FieldViolation::field).toList()).containsExactly("size");
    }
}
