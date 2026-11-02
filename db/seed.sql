-- Carga inicial DETERMINÍSTICA: sem random(), sem datas. Executar duas vezes (após TRUNCATE)
-- produz exatamente as mesmas linhas, com os mesmos UUIDs, em qualquer ambiente.
--
-- Uso:  psql -v rows=10000 -f seed.sql      (padrão: 10000 linhas)
--
-- Observações para a metodologia:
--   * Textos só com caracteres ASCII, para que a busca por título (ILIKE) não dependa da
--     localidade (locale) do banco.
--   * ISBNs são SINTÉTICOS ("978" + 10 dígitos): têm formato válido, mas o dígito verificador
--     não é real. Um a cada 10 livros não tem ISBN (NULL).
--   * Ids = md5('library-cloud-benchmark:book:' || n) convertido para UUID.

\if :{?rows}
\else
\set rows 10000
\endif

INSERT INTO books (id, titulo, autor, isbn, ano_publicacao, categoria, disponivel)
SELECT
    md5('library-cloud-benchmark:book:' || n)::uuid,
    (ARRAY['Dom','Senhora','Memorias','Grande','Vidas','Capitaes','Iracema','Quincas','Sagarana','Macunaima'])[1 + (n % 10)]
        || ' ' ||
    (ARRAY['Casmurro','Postumas','Sertao','Secas','Areia','Borba','Veredas','Perfil','Noite','Aurora'])[1 + ((n / 10) % 10)]
        || ' vol. ' || n,
    (ARRAY['Machado de Assis','Jose de Alencar','Clarice Lispector','Graciliano Ramos',
           'Guimaraes Rosa','Rachel de Queiroz','Lima Barreto','Cecilia Meireles'])[1 + (n % 8)],
    CASE WHEN n % 10 = 0 THEN NULL ELSE '978' || lpad(n::text, 10, '0') END,
    1950 + (n % 75),
    (ARRAY['Romance','Poesia','Conto','Ensaio','Biografia','Historia'])[1 + (n % 6)],
    (n % 7) <> 0
FROM generate_series(1, :rows) AS n;

ANALYZE books;
