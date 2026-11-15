# Solução de problemas

Formato: **Sintoma** · **Causa** · **Solução** · **Etapa/data**.

## T-001 — Script `.ps1` bloqueado
- **Sintoma:** `a execução de scripts foi desabilitada neste sistema` ao rodar `.\scripts\db-reset.ps1`.
- **Causa:** política de execução do PowerShell (padrão restritivo no Windows).
- **Solução:** `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` (vale só para a janela atual; repetir em janelas novas).
- **Etapa 3, 02/11/2026.**

## T-002 — Campo extra `"up": true` em `/health/ready`
- **Sintoma:** a resposta trazia `status`, `database` e também `up`, fora do contrato.
- **Causa:** o método `isUp()` do registro `ReadinessResponse` foi tratado pelo Jackson como propriedade.
- **Solução:** renomear para `up()` (e atualizar `HealthController` e o teste). Resposta passou a ter só os dois campos.
- **Etapa 3, 02/11/2026.** Regra preventiva em `implementation.md`.

## T-003 — `Show-Error` sem o corpo da resposta
- **Sintoma:** só aparecia `HTTP 409` / `HTTP 404`, sem o JSON.
- **Causa:** em Windows PowerShell 5.1, `$_.ErrorDetails.Message` pode vir vazio.
- **Solução:** a função passou a ler o corpo direto de `$_.Exception.Response.GetResponseStream()` quando `ErrorDetails` está vazio (ver `README.md`). A versão do PowerShell do autor **ainda não foi confirmada** (`$PSVersionTable.PSVersion`); o comportamento é compatível com a 5.1.
- **Etapa 3, 02/11/2026.**

## T-004 — PowerShell esconde propriedades ao exibir objetos seguidos
- **Sintoma:** `/health/ready` aparecia só com `status`; o `$created` aparecia sem `id`; o PUT sem `anoPublicacao` e `categoria`.
- **Causa:** a exibição em tabela/lista reaproveita as colunas do primeiro objeto de tipo parecido. Não era problema da API (confirmado com JSON completo).
- **Solução:** usar `| ConvertTo-Json` ao conferir respostas.
- **Etapa 3, 02/11/2026.**

## T-005 — Acentos estranhos ao exibir o `.env`
- **Sintoma:** comentários do `.env` apareciam como `sÃ£o`.
- **Causa:** exibição do PowerShell 5.1 lendo UTF-8 como ANSI. Afeta só comentários; os valores das variáveis são ASCII.
- **Solução:** nenhuma necessária.
- **Etapa 3, 02/11/2026.**

## T-006 — Resposta de rota inexistente em outro formato
- **Sintoma:** `/api/nada` responde `{"timestamp","status","error","path"}`.
- **Causa:** erro gerado pelo framework, fora do tratador da aplicação.
- **Solução:** não é defeito; diferença documentada entre adaptadores (`architecture.md`).
- **Etapa 3, 02/11/2026.**

## T-007 — Saída colada incompleta
- **Sintoma:** linhas do `.env` e de `docker compose ps` ausentes em colagens.
- **Causa:** perda na cópia do terminal; a conferência por nomes das variáveis confirmou as 10 variáveis.
- **Solução:** conferir sempre com comandos que listem só os nomes (sem expor senhas).
- **Etapa 3, 02/11/2026.**

## T-008 — Pesquisa no Functions respondia 400 de `id` (rota `search` capturada por `{id}`)
- **Sintoma:** `GET /api/books/search?title=dom%20casmurro&size=2` (e também `?title=dom&size=2`) respondia `400` com `{"code":"VALIDATION_ERROR",...,"violations":[{"field":"id","message":"deve ser um UUID válido"}]}`. A listagem `GET /api/books?size=3` funcionava (10000 elementos, 3334 páginas, primeiro título `Capitaes Areia vol. 1045`).
- **Causa:** o log do `func start` mostrava `Executing 'Functions.getBook'` para essas requisições: o host entregou a pesquisa à função da rota `api/books/{id}` com `id = "search"`. A função `searchBooks`, de rota literal `api/books/search`, não teve precedência.
- **Solução:** a função `searchBooks` foi removida e a escolha é feita no código de `getBook`: quando o segmento é exatamente `search`, ela executa a pesquisa; caso contrário, a consulta por id (mesmo comportamento do Spring, que distingue maiúsculas). A adaptação passou a ter 7 funções. **Correção confirmada**: os cenários de pesquisa do `api-smoke.ps1` ficaram idênticos nos dois adaptadores (14/11/2026).
- **Etapa 5, 14/11/2026** (saídas informadas pelo autor).

## T-009 — `func start` falha com `NoSuchMethodException` de função removida
- **Sintoma:** após remover a função `searchBooks` do código e rodar `mvn -pl adapter-functions -am package` (sem `clean`), o `func start` listou 8 funções, inclusive `searchBooks`, e o worker registrou `Worker failed to load function: 'searchBooks'` com `NoSuchMethodException: There are no methods named "searchBooks" in class br.edu.tcc.librarycloud.functions.BookFunctions`.
- **Causa:** a pasta `target\azure-functions\library-cloud-benchmark-fn-local` manteve a pasta (e o `function.json`) da função removida; o JAR novo já não tinha o método.
- **Solução:** empacotar com `clean`: `mvn -pl adapter-functions -am clean package -DskipTests` (com o `func start` parado e o terminal fora da pasta empacotada, pois o Windows não apaga pastas em uso). O `adapter-functions\local.settings.json` fica fora de `target` e é copiado de novo pelo `package`.
- **Etapa 5, 14/11/2026** (saída informada pelo autor).

## T-010 — `Content-Type: text/plain` no 204 do Functions (diferença inerente)
- **Sintoma:** o `Compare-Object` entre `smoke-container.txt` e `smoke-functions.txt` listou apenas linhas `content-type`. Nos três cenários `DELETE` com status 204, o Functions responde `content-type: text/plain` e o container não envia `Content-Type`. Status e corpos (inclusive de todas as respostas de erro) não apareceram como diferentes.
- **Causa:** o runtime do Functions acrescenta `Content-Type` a respostas sem corpo; o Spring/Tomcat não. Sem corpo, o cabeçalho não tem significado para o cliente.
- **Solução:** nenhuma no código; **diferença inerente documentada** (`architecture.md`). O `api-smoke.ps1` passou a registrar `(sem corpo)` no lugar do `Content-Type` quando a resposta não tem corpo, para o `Compare-Object` ficar sem ruído.
- **Confirmação:** com o script ajustado, os dois registros foram repetidos em 14/11/2026 (42 cenários cada) e o `Compare-Object` **não listou nenhuma linha**. (A contagem de linhas da primeira saída colada não fechava com os 3 cenários; foi tratada como perda na colagem.)
- **Etapa 5, 14/11/2026** (saídas informadas pelo autor).

## Problemas conhecidos (preventivo, ainda sem ocorrência)

- **`Could not find a valid Docker environment` ao rodar `mvn test -Pintegration`:** o Docker Desktop não está aberto ou o motor ainda não iniciou. Abrir o Docker Desktop, aguardar "Engine running" e repetir. Se aparecer `client version ... is too old`, a versão do Testcontainers não corresponde ao Docker instalado; conferir a versão gerenciada pelo BOM com `mvn dependency:tree`.
- **Primeira execução lenta dos testes de integração:** o Docker baixa a imagem auxiliar do Testcontainers e as dependências; execuções seguintes são mais rápidas.

### Etapa 5 (Azure Functions local) — preventivo, sem ocorrência

- **`func` não é reconhecido:** o Core Tools não está no `PATH` ou o terminal é anterior à instalação. Fechar e reabrir o terminal; `func --version`.
- **Todas as rotas respondem `500 INTERNAL_ERROR`:** o log da função mostra a lista de variáveis `DB_*` ausentes. A pasta empacotada não tem `local.settings.json` (o `package` foi executado antes do `new-local-settings.ps1`). Rodar `.\scripts\new-local-settings.ps1` (ele grava também na pasta empacotada, se existir) e reiniciar o `func start`.
- **`func start` lista 0 funções ou o worker Java não inicia:** conferir `java -version`, `echo $env:JAVA_HOME` e a versão do Core Tools; registrar a saída completa aqui (o suporte ao Java 21 no Core Tools local no Windows ainda não foi confirmado).
- **Pasta `target\azure-functions\library-cloud-benchmark-fn-local` ausente após o `package`:** registrar a saída completa do Maven (objetivo `azure-functions:package`).
- **Mensagens sobre `AzureWebJobsStorage` no `func start`:** as funções são só HTTP e o guia exige armazenamento local apenas para gatilhos de Blob/Fila/Tabela; se aparecer aviso ou erro, registrar o texto exato aqui.
- **Porta 7071 em uso:** outra instância do `func start`; encerrar com `Ctrl+C`.
- **`Compare-Object` lista diferenças entre os registros do smoke:** abrir os dois arquivos, localizar o cenário (`### nome`) e classificar como defeito (corrigir) ou diferença inerente (documentar em `architecture.md`).

### Etapa 6 (imagem Docker) — preventivo, sem ocorrência

- **`docker build` falha ao baixar dependências do Maven:** rede instável; repetir o comando (o cache preserva o que já foi baixado). Se persistir, registrar a mensagem completa.
- **`docker build` não encontra a imagem `maven:3.9-eclipse-temurin-21` ou `eclipse-temurin:21-jre-noble`:** a tag pode ter mudado; registrar a mensagem e ajustar o `Dockerfile` (e o ADR-022).
- **Contexto de build muito grande ou segredos na imagem:** conferir o `.dockerignore` (deve excluir `.env`, `target/` e `.git/`); `docker build` mostra o tamanho do contexto transferido.
- **`run-image.ps1` não acha o banco ou a rede:** `docker compose up -d`; conferir `docker ps` (o contêiner deve se chamar `library-postgres`).
- **API na imagem sobe, mas não conecta ao banco:** o host dentro da rede do compose é `postgres` (não `localhost`); conferir `DB_USER` e `DB_PASSWORD` do `.env` e os logs do contêiner.
- **`container name "/library-api-local" is already in use`:** `docker rm -f library-api-local`.
- **`port is already allocated` (8081):** usar `-Port` com outro valor.
- **`docker stop` demora cerca de 10 s e encerra à força:** a JVM pode não ter tratado o `SIGTERM`; registrar o tempo e as últimas linhas do log.
