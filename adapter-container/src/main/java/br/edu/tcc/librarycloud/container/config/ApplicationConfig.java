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
import org.springframework.core.env.Environment;

/**
 * Montagem manual dos objetos: o núcleo e a infraestrutura não usam anotações do Spring,
 * então é aqui que o Spring os conhece. A versão Functions fará a mesma montagem com
 * {@code new}, usando as MESMAS classes.
 */
@Configuration
public class ApplicationConfig {

    /**
     * Lê DB_URL, DB_USER etc. pelo {@link Environment} do Spring. Em produção isso equivale a ler
     * as variáveis de ambiente do sistema; nos testes permite informar os valores por argumentos.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(Environment environment) {
        return DataSourceFactory.create(DatabaseConfig.from(name -> environment.getProperty(name)));
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
