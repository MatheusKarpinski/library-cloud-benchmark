-- Esquema do banco de dados (PostgreSQL 17). Idempotente: pode ser executado várias vezes.
-- Os limites de tamanho espelham as regras de Book.java (ADR-010).

CREATE TABLE IF NOT EXISTS books (
    id              UUID         PRIMARY KEY,
    titulo          VARCHAR(200) NOT NULL,
    autor           VARCHAR(150) NOT NULL,
    isbn            VARCHAR(13),                     -- normalizado (sem hífens); opcional
    ano_publicacao  INTEGER      NOT NULL,
    categoria       VARCHAR(80)  NOT NULL,
    disponivel      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_books_isbn UNIQUE (isbn),          -- vários NULL são permitidos
    CONSTRAINT ck_books_ano  CHECK (ano_publicacao >= 1000)
);

-- Acelera a listagem ordenada (ORDER BY lower(titulo), id) usada por GET /api/books.
CREATE INDEX IF NOT EXISTS idx_books_titulo_ordem ON books (lower(titulo), id);
