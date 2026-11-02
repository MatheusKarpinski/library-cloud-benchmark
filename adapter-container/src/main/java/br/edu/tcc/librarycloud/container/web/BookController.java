package br.edu.tcc.librarycloud.container.web;

import br.edu.tcc.librarycloud.application.BookService;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.contract.BookRequest;
import br.edu.tcc.librarycloud.contract.BookResponse;
import br.edu.tcc.librarycloud.contract.PageResponse;
import br.edu.tcc.librarycloud.domain.Book;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Adaptador HTTP (Spring MVC). Só traduz HTTP <-> chamadas ao {@link BookService};
 * nenhuma regra de negócio mora aqui. Erros viram HTTP em {@link ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public PageResponse<BookResponse> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return PageResponse.from(bookService.list(PageRequest.of(page, size)), BookResponse::from);
    }

    @GetMapping("/search")
    public PageResponse<BookResponse> search(
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return PageResponse.from(
                bookService.searchByTitle(title, PageRequest.of(page, size)), BookResponse::from);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable("id") String id) {
        return BookResponse.from(bookService.getById(id));
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@RequestBody BookRequest request) {
        Book created = bookService.create(request.toBookData());
        return ResponseEntity
                .created(URI.create("/api/books/" + created.id()))
                .body(BookResponse.from(created));
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable("id") String id, @RequestBody BookRequest request) {
        return BookResponse.from(bookService.update(id, request.toBookData()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        bookService.delete(id);
    }
}
