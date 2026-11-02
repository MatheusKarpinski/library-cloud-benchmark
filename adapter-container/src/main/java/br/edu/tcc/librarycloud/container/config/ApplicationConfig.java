package br.edu.tcc.librarycloud.container.config;

import br.edu.tcc.librarycloud.application.BookService;
import br.edu.tcc.librarycloud.application.port.BookRepository;
import br.edu.tcc.librarycloud.infrastructure.config.DataSourceFactory;
import br.edu.tcc.librarycloud.infrastructure.config.DatabaseConfig;
import br.edu.tcc.librarycloud.infrastructure.persistence.DatabaseHealthChecker;
import br.edu.tcc.librarycloud.infrastructure.persistence.JdbcBookRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Montagem manual dos objetos: o núcleo e a infraestrutura não usam anotações do Spring,
 * então é aqui que o Spring os conhece. A versão Functions fará a mesma montagem com
 * {@code new}, usando as MESMAS classes.
 */
@Configuration
public class ApplicationConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource() {
        return DataSourceFactory.create(DatabaseConfig.fromEnvironment());
    }

    @Bean
    public BookRepository bookRepository(HikariDataSource dataSource) {
        return new JdbcBookRepository(dataSource);
    }

    @Bean
    public BookService bookService(BookRepository bookRepository) {
        return new BookService(bookRepository);
    }

    @Bean
    public DatabaseHealthChecker databaseHealthChecker(HikariDataSource dataSource) {
        return new DatabaseHealthChecker(dataSource);
    }
}
