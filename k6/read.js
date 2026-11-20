// Teste de CARGA DE LEITURA (Etapa 7 / ADR-023): mistura de listagem paginada, consulta por id
// e pesquisa por titulo. Cada iteracao faz UMA requisicao (modelo de taxa de chegada).
// Execucao recomendada: .\scripts\run-k6.ps1 -Target container -Test read -Level smoke
import { check } from 'k6';
import { call, checkApi, field } from './lib/api.js';
import {
  READ_ENDPOINTS, SEARCH_TERMS, buildOptions, pickOperation, resolveConfig, summaryOutputs,
} from './lib/common.js';

const cfg = resolveConfig(__ENV, 'read');
export const options = buildOptions(cfg, READ_ENDPOINTS);

const ID_PAGE_SIZE = 100;

export function setup() {
  const total = checkApi(cfg);
  if (total < 1) {
    throw new Error('O banco esta vazio. Restaure a carga: .\\scripts\\db-reset.ps1 -Rows 10000');
  }

  // Amostra de ids espalhada por todo o conjunto (paginas igualmente espacadas).
  const tags = { phase: 'setup' };
  const totalPages = Math.max(1, Math.ceil(total / ID_PAGE_SIZE));
  const sample = Math.min(cfg.idSamplePages, totalPages);
  const ids = [];
  for (let i = 0; i < sample; i++) {
    const page = Math.floor((i * totalPages) / sample);
    const response = call(cfg, 'GET', `/api/books?page=${page}&size=${ID_PAGE_SIZE}`, null,
      'setup', 'GET /api/books?page&size', tags);
    const items = field(response, 'items');
    if (response.status === 200 && Array.isArray(items)) {
      for (const item of items) ids.push(item.id);
    }
  }
  if (ids.length === 0) {
    throw new Error('Nao foi possivel coletar ids para a consulta por id.');
  }
  return { total, ids };
}

export default function (data) {
  const operation = pickOperation(cfg.mix, Math.random());

  if (operation === 'list') {
    const totalPages = Math.max(1, Math.ceil(data.total / cfg.pageSize));
    const page = Math.floor(Math.random() * totalPages);
    const response = call(cfg, 'GET', `/api/books?page=${page}&size=${cfg.pageSize}`, null,
      'list', 'GET /api/books');
    check(response, {
      'list: status 200': (r) => r.status === 200,
      'list: items e uma lista': (r) => Array.isArray(field(r, 'items')),
    });
  } else if (operation === 'get') {
    const id = data.ids[Math.floor(Math.random() * data.ids.length)];
    const response = call(cfg, 'GET', `/api/books/${id}`, null, 'get', 'GET /api/books/{id}');
    check(response, {
      'get: status 200': (r) => r.status === 200,
      'get: id devolvido igual ao pedido': (r) => field(r, 'id') === id,
    });
  } else {
    const term = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];
    const response = call(cfg, 'GET',
      `/api/books/search?title=${encodeURIComponent(term)}&size=${cfg.pageSize}`, null,
      'search', 'GET /api/books/search');
    check(response, {
      'search: status 200': (r) => r.status === 200,
      'search: items e uma lista': (r) => Array.isArray(field(r, 'items')),
    });
  }
}

export function handleSummary(data) {
  return summaryOutputs(data, cfg, READ_ENDPOINTS);
}
