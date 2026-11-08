package br.edu.tcc.librarycloud.container;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sobe a aplicação REAL (Spring Boot + Tomcat) numa porta livre e oferece um cliente HTTP simples.
 * Os testes falam com a API por HTTP de verdade, como o k6 fará. Argumentos de linha de comando
 * têm prioridade sobre variáveis de ambiente do sistema, então o ambiente do desenvolvedor
 * não interfere nos testes.
 */
final class RunningApplication implements AutoCloseable {

    private final ConfigurableApplicationContext context;
    private final int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private RunningApplication(ConfigurableApplicationContext context, int port) {
        this.context = context;
        this.port = port;
    }

    static RunningApplication start(String... applicationArgs) throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        List<String> args = new ArrayList<>(List.of(
                "--server.port=" + port,
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN"));
        args.addAll(List.of(applicationArgs));
        ConfigurableApplicationContext context =
                SpringApplication.run(LibraryApplication.class, args.toArray(new String[0]));
        return new RunningApplication(context, port);
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send("GET", path, null);
    }

    HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        return send("POST", path, json);
    }

    HttpResponse<String> put(String path, String json) throws IOException, InterruptedException {
        return send("PUT", path, json);
    }

    HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        return send("DELETE", path, null);
    }

    private HttpResponse<String> send(String method, String path, String json)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        context.close();
    }
}
