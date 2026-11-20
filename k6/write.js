// Teste de CARGA DE ESCRITA (Etapa 7 / ADR-023): cada iteracao e um ciclo completo
// POST (cria) -> PUT (atualiza) -> DELETE (exclui), de modo que o banco volta ao tamanho original
// ao final (se nao houver falhas). 1 iteracao = 3 requisicoes. O ISBN sintetico de cada livro
// (prefixo 979) e unico na execucao: nao colide com a carga padrao (prefixo 978) nem entre VUs.
// Execucao recomendada: .\scripts\run-k6.ps1 -Target container -Test write -Level smoke -ResetDb
import { check } from 'k6';
import exec from 'k6/execution';
import { call, checkApi, field } from './lib/api.js';
import { WRITE_ENDPOINTS, buildBook, buildOptions, resolveConfig, summaryOutputs } from './lib/common.js';

const cfg = resolveConfig(__ENV, 'write');
export const options = buildOptions(cfg, WRITE_ENDPOINTS);

export function setup() {
  const total = checkApi(cfg);
  return { totalBefore: total };
}

export default function () {
  const counter = exec.scenario.iterationInTest;

  const created = call(cfg, 'POST', '/api/books', buildBook(cfg.runSeed, counter, false),
    'create', 'POST /api/books');
  const createdOk = check(created, {
    'create: status 201': (r) => r.status === 201,
    'create: id presente': (r) => typeof field(r, 'id') === 'string',
  });
  if (!createdOk) return;
  const id = field(created, 'id');

  const updated = call(cfg, 'PUT', `/api/books/${id}`, buildBook(cfg.runSeed, counter, true),
    'update', 'PUT /api/books/{id}');
  check(updated, {
    'update: status 200': (r) => r.status === 200,
    'update: disponivel falso': (r) => field(r, 'disponivel') === false,
  });

  // A exclusao e feita mesmo que a atualizacao tenha falhado, para nao deixar lixo no banco.
  const deleted = call(cfg, 'DELETE', `/api/books/${id}`, null, 'delete', 'DELETE /api/books/{id}');
  check(deleted, { 'delete: status 204': (r) => r.status === 204 });
}

export function teardown(data) {
  const after = call(cfg, 'GET', '/api/books?size=1', null, 'teardown', 'GET /api/books?size=1',
    { phase: 'teardown' });
  const total = field(after, 'totalElements');
  const diff = typeof total === 'number' ? total - data.totalBefore : 'n/d';
  console.log(`Livros antes: ${data.totalBefore} | depois: ${total} | diferenca: ${diff}`
    + ' (esperado 0 se todos os ciclos terminaram)');
}

export function handleSummary(data) {
  return summaryOutputs(data, cfg, WRITE_ENDPOINTS);
}
