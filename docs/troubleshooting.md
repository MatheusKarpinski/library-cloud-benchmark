# Solução de problemas

Formato: **Sintoma** · **Causa** · **Solução** · **Etapa/data**.

## T-001 — Script `.ps1` bloqueado
- **Sintoma:** `a execução de scripts foi desabilitada neste sistema` ao rodar `.\scripts\db-reset.ps1`.
- **Causa:** política de execução do PowerShell (padrão restritivo no Windows).
- **Solução:** `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` (vale só para a janela atual; repetir em janelas novas). Voltou a ocorrer ao reabrir os terminais na Etapa 6 (15/11/2026). Voltou a ocorrer ao reabrir o terminal na Etapa 7 (20/11/2026).
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

## T-011 — `docker image inspect` da imagem base falha com "No such image"
- **Sintoma:** `Error response from daemon: No such image: eclipse-temurin:21-jre-noble` ao tentar obter o digest da base, no passo de inspeção da imagem.
- **Causa:** o `docker build` baixa a imagem base para o cache do construtor e não a deixa salva como imagem local com nome; `docker image inspect` só enxerga imagens locais. Foi um erro do roteiro da documentação, não da imagem construída.
- **Solução:** consultar o digest no registro: `docker buildx imagetools inspect eclipse-temurin:21-jre-noble | Select-String "^Digest:"`. O valor reflete a tag no momento da consulta; se a tag mudar entre o build e a consulta, pode diferir do usado no build. README e `testing.md` foram corrigidos.
- **Etapa 6, 15/11/2026** (saída informada pelo autor).

## T-012 — Dica "What's next / Gordon" do Docker Desktop após comando com código de saída diferente de zero
- **Sintoma:** mensagem "What's next: Debug this container error with Gordon" depois de parar a API (`docker stop`) e depois de `docker run ... sh -c "ls -la /app; env | grep -c DB_"`.
- **Causa (provável):** o Docker Desktop mostra a dica quando o contêiner termina com código de saída diferente de zero. Nesses dois casos, a JVM encerrada por `SIGTERM` costuma sair com 143 (não verificado aqui) e o `grep -c` sem correspondências retorna 1. Não houve falha da aplicação: o log mostra o *graceful shutdown* completo.
- **Solução:** nenhuma; ignorar a dica nesses casos.
- **Etapa 6, 15/11/2026** (saídas informadas pelo autor).

## T-013 — Resumo do k6 com 163 requisições (em vez de 150) e `n/d` por endpoint
- **Sintoma:** no smoke de leitura (`20261120-180243`), o resumo mostrou 163 requisições para 151 iterações (a leitura faz uma requisição por iteração) e `n/d` na coluna de requisições de cada endpoint. Falhas, checks, limites e latências estavam coerentes.
- **Causa:** `TREND_STATS` (em `k6/lib/common.js`) não incluía `count`, então a contagem das métricas de latência não estava disponível no `handleSummary`. O resumo usou o total de `http_reqs`, que inclui as 12 requisições do `setup` (2 de verificação e 10 páginas de ids). A causa foi identificada pela leitura do código; a confirmação veio da nova execução.
- **Solução:** `count` incluído em `TREND_STATS`.
- **Confirmação:** nova execução (`20261120-181002`): 150 requisições para 150 iterações e contagens por endpoint (78, 44 e 28) somando 150.
- **Etapa 7, 20/11/2026** (saídas informadas pelo autor). Observação: a documentação do k6 sobre `summaryTrendStats` não foi consultada (ver `tcc-notes.md`, C29).

## T-014 — Execução abortada no `setup` gravava um `.summary.json` com zeros
- **Sintoma:** com o alvo parado, o teste terminou com a mensagem "API indisponivel" e código 107, mas gravou `results\...-read-container-smoke.summary.json` com requisições 0, `falhas: 0.00%`, `iteracoes: n/d` e "nenhum limite violado".
- **Causa:** o k6 chama o `handleSummary` mesmo quando o `setup` lança exceção; o script gravava o arquivo sem verificar se alguma iteração foi executada. Um arquivo assim poderia ser confundido com uma medição válida.
- **Solução:** `summaryOutputs` não grava arquivo quando não há iterações e imprime "nenhuma iteracao executada"; o `run-k6.ps1` informa "Arquivo de resumo nao encontrado". O arquivo gerado na primeira tentativa foi removido pelo autor.
- **Confirmação:** nova execução com o alvo parado (`20261120-183431`): código 107, nenhuma linha nova em `results\`; e um smoke normal na imagem (`20261120-183505`) gerou o arquivo como antes.
- **Etapa 7, 20/11/2026** (saídas informadas pelo autor).

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

### Etapa 6 (imagem Docker) — preventivo (os itens abaixo não ocorreram; as ocorrências reais são T-011 e T-012)

- **`docker build` falha ao baixar dependências do Maven:** rede instável; repetir o comando (o cache preserva o que já foi baixado). Se persistir, registrar a mensagem completa.
- **`docker build` não encontra a imagem `maven:3.9-eclipse-temurin-21` ou `eclipse-temurin:21-jre-noble`:** a tag pode ter mudado; registrar a mensagem e ajustar o `Dockerfile` (e o ADR-022).
- **Contexto de build muito grande ou segredos na imagem:** conferir o `.dockerignore` (deve excluir `.env`, `target/` e `.git/`); `docker build` mostra o tamanho do contexto transferido.
- **`run-image.ps1` não acha o banco ou a rede:** `docker compose up -d`; conferir `docker ps` (o contêiner deve se chamar `library-postgres`).
- **API na imagem sobe, mas não conecta ao banco:** o host dentro da rede do compose é `postgres` (não `localhost`); conferir `DB_USER` e `DB_PASSWORD` do `.env` e os logs do contêiner.
- **`container name "/library-api-local" is already in use`:** `docker rm -f library-api-local`.
- **`port is already allocated` (8081):** usar `-Port` com outro valor.
- **`docker stop` demora cerca de 10 s e encerra à força:** a JVM pode não ter tratado o `SIGTERM`; registrar o tempo e as últimas linhas do log.

### Etapa 7 (k6) — preventivo (os itens abaixo não ocorreram; as ocorrências reais são T-013 e T-014)

- **`k6` não é reconhecido após o `winget install`:** o VS Code mantém o `PATH` antigo; fechar e reabrir o VS Code por completo (não só o terminal).
- **"API indisponivel em ..." no início do teste:** o alvo do `-Target` não está rodando; subir o JAR, o `func start` ou a imagem.
- **"O banco esta vazio":** restaurar a carga com `.\scripts\db-reset.ps1 -Rows 10000` ou usar `-ResetDb`.
- **Falhas `409` no teste de escrita:** ISBN de uma execução interrompida ainda no banco; usar `-ResetDb`.
- **Diferença diferente de zero no log do `teardown`:** algum DELETE falhou; restaurar o banco antes do próximo teste.
- **Código de saída 99:** limite violado (não é erro de script); ver o resumo.
- **`descartadas` maior que zero ou aviso de VUs insuficientes:** a API ou o gerador não acompanhou a taxa; aumentar `-MaxVus` ou reduzir `-Rate`, e registrar o fato como resultado.
- **Arquivo `.summary.json` não gravado:** o diretório `results` não existe (execução direta do k6 sem o `run-k6.ps1`), a execução não teve nenhuma iteração (ex.: API parada; ver T-014) ou o resumo falhou; ver as mensagens do k6 ao final.
- **Campos `n/d` no resumo do terminal:** a estrutura do objeto do `handleSummary` mudou na versão do k6 ou falta uma estatística em `TREND_STATS` (ver T-013); o arquivo `.summary.json` guarda o objeto completo em `data` (anexar o arquivo ao relato).
- **O log da API na imagem mostra a porta 8080, mas o teste usa 8081:** a 8080 é a porta dentro do contêiner; o `run-image.ps1` a publica como 8081 no Windows (dúvida do autor, sem falha).
- **Linhas do resumo ausentes no terminal (`alvo=...`, latência geral):** observado em algumas saídas coladas, causa não determinada; usar o `.summary.json`, que guarda os valores.
