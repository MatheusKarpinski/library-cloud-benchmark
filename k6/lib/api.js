// Chamadas HTTP e verificacoes compartilhadas pelos scripts de leitura e de escrita (Etapa 7).
import http from 'k6/http';

const HEADERS = { 'Content-Type': 'application/json' };

/**
 * `endpoint` identifica a operacao (list, get, search, create, update, delete); `name` agrupa
 * as URLs com ids em um unico nome (evita uma serie de metricas por id).
 */
export function call(cfg, method, path, body, endpoint, name, extraTags) {
  const url = cfg.baseUrl + path;
  const params = { headers: HEADERS, tags: Object.assign({ endpoint, name }, extraTags || {}) };
  if (method === 'GET') return http.get(url, params);
  if (method === 'DELETE') return http.del(url, null, params);
  const payload = JSON.stringify(body);
  return method === 'POST' ? http.post(url, payload, params) : http.put(url, payload, params);
}

/** Leitura de campo do corpo JSON sem lancar excecao (corpo vazio ou invalido => undefined). */
export function field(response, selector) {
  try {
    return response.json(selector);
  } catch (e) {
    return undefined;
  }
}

/**
 * Verificacao antes da carga: a API responde e o banco tem dados. Falha com mensagem clara
 * (o k6 interrompe a execucao se o setup lancar excecao). As requisicoes do setup levam a tag
 * phase=setup e ficam fora das metricas da fase de carga.
 */
export function checkApi(cfg) {
  const tags = { phase: 'setup' };
  const ready = call(cfg, 'GET', '/health/ready', null, 'setup', 'GET /health/ready', tags);
  if (ready.status !== 200) {
    throw new Error(`API indisponivel em ${cfg.baseUrl} (GET /health/ready => ${ready.status}). `
      + 'Suba a API e o banco antes de rodar o teste.');
  }
  const first = call(cfg, 'GET', '/api/books?size=1', null, 'setup', 'GET /api/books?size=1', tags);
  const total = field(first, 'totalElements');
  if (first.status !== 200 || typeof total !== 'number') {
    throw new Error(`Nao foi possivel ler a listagem (GET /api/books?size=1 => ${first.status}).`);
  }
  return total;
}
