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
