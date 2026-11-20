// Funcoes PURAS (sem dependencia de modulos do k6): configuracao por variaveis de ambiente,
// montagem de cenarios e limites, geracao de dados e resumo final. Etapa 7 / ADR-023.
// Todos os valores vem de variaveis de ambiente (em k6, __ENV); veja README.md, secao "Etapa 7".

// Niveis de carga PROVISORIOS: servem para validar os scripts na maquina local. Os niveis das
// medicoes finais serao definidos e calibrados na metodologia (Etapa 10). Todos podem ser
// sobrescritos por RATE, DURATION e MAX_VUS.
export const LEVELS = {
  smoke: { rate: 5, duration: '30s', maxVus: 20 },
  low: { rate: 20, duration: '2m', maxVus: 100 },
  medium: { rate: 100, duration: '3m', maxVus: 300 },
  high: { rate: 300, duration: '3m', maxVus: 1000 },
};

// Termos de pesquisa existentes na carga padrao (db/seed.sql): 100 titulos-base x 100 volumes.
// Termos de duas palavras casam ~100 livros; de uma palavra, ~1000 (com 10000 linhas).
export const SEARCH_TERMS = [
  'Dom Casmurro', 'Memorias Postumas', 'Capitaes Areia', 'Quincas Borba',
  'Macunaima Aurora', 'Casmurro', 'Areia', 'Veredas',
];

export const READ_ENDPOINTS = ['list', 'get', 'search'];
export const WRITE_ENDPOINTS = ['create', 'update', 'delete'];

export const TREND_STATS = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'];

function intValue(raw, fallback, name, min) {
  if (raw === undefined || raw === null || String(raw).trim() === '') return fallback;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < min) {
    throw new Error(`${name} invalido: "${raw}" (esperado inteiro >= ${min})`);
  }
  return n;
}

function floatValue(raw, fallback, name) {
  if (raw === undefined || raw === null || String(raw).trim() === '') return fallback;
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 0) throw new Error(`${name} invalido: "${raw}"`);
  return n;
}

/** Converte textos como "30s", "2m", "1h" ou "1m30s" em segundos. */
export function parseDuration(text) {
  const value = String(text).trim();
  if (!/^(\d+[hms])+$/.test(value)) {
    throw new Error(`DURATION invalida: "${text}" (exemplos: 30s, 2m, 1m30s)`);
  }
  const units = { h: 3600, m: 60, s: 1 };
  let seconds = 0;
  const part = /(\d+)([hms])/g;
  let match = part.exec(value);
  while (match !== null) {
    seconds += Number(match[1]) * units[match[2]];
    match = part.exec(value);
  }
  if (seconds < 1) throw new Error(`DURATION invalida: "${text}" (minimo 1s)`);
  return seconds;
}

/** Le e valida todas as variaveis de ambiente. `test` e "read" ou "write". */
export function resolveConfig(env, test) {
  const level = String(env.LEVEL || 'smoke').toLowerCase();
  const base = LEVELS[level];
  if (!base) {
    throw new Error(`LEVEL invalido: "${env.LEVEL}" (use ${Object.keys(LEVELS).join(', ')})`);
  }
  const profile = String(env.PROFILE || 'constant').toLowerCase();
  if (profile !== 'constant' && profile !== 'ramp') {
    throw new Error(`PROFILE invalido: "${env.PROFILE}" (use constant ou ramp)`);
  }

  const rate = intValue(env.RATE, base.rate, 'RATE', 1);
  const duration = String(env.DURATION || base.duration);
  const durationSeconds = parseDuration(duration);
  const maxVus = intValue(env.MAX_VUS, base.maxVus, 'MAX_VUS', 1);
  const preVus = Math.min(maxVus, intValue(env.PRE_VUS, Math.max(5, Math.ceil(rate / 10)), 'PRE_VUS', 1));

  const rampSteps = String(env.RAMP_STEPS || '0.25,0.5,0.75,1').split(',').map((s) => Number(s.trim()));
  if (rampSteps.length < 1 || rampSteps.some((f) => !Number.isFinite(f) || f <= 0 || f > 1)) {
    throw new Error(`RAMP_STEPS invalido: "${env.RAMP_STEPS}" (fracoes em (0,1], ex.: 0.25,0.5,1)`);
  }

  const target = String(env.TARGET || 'local');
  const mix = {
    list: intValue(env.MIX_LIST, 50, 'MIX_LIST', 0),
    get: intValue(env.MIX_GET, 30, 'MIX_GET', 0),
    search: intValue(env.MIX_SEARCH, 20, 'MIX_SEARCH', 0),
  };
  if (test === 'read' && mix.list + mix.get + mix.search < 1) {
    throw new Error('MIX_LIST, MIX_GET e MIX_SEARCH nao podem ser todos zero');
  }

  return {
    test,
    level,
    profile,
    rate,
    duration,
    durationSeconds,
    maxVus,
    preVus,
    rampSteps,
    baseUrl: String(env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, ''),
    target,
    runId: String(env.RUN_ID || `${test}-${target}-${level}`),
    resultsDir: String(env.RESULTS_DIR || 'results').replace(/[\\/]+$/, ''),
    p95Ms: intValue(env.P95_MS, 1000, 'P95_MS', 1),
    maxErrorRate: floatValue(env.MAX_ERROR_RATE, 0.01, 'MAX_ERROR_RATE'),
    pageSize: intValue(env.PAGE_SIZE, 20, 'PAGE_SIZE', 1),
    idSamplePages: intValue(env.ID_SAMPLE_PAGES, 10, 'ID_SAMPLE_PAGES', 1),
    runSeed: intValue(env.RUN_SEED, Date.now() % 1000, 'RUN_SEED', 0) % 1000,
    mix,
  };
}

/**
 * Modelo ABERTO (taxa de chegada): o k6 inicia `rate` iteracoes por segundo, independentemente
 * do tempo de resposta. Leitura: 1 iteracao = 1 requisicao. Escrita: 1 iteracao = 3 requisicoes
 * (POST, PUT, DELETE). `ramp`: degraus com subida de 10% e patamar de 90% do tempo de cada degrau.
 */
export function buildScenarios(cfg, name) {
  const tags = { phase: 'load' };
  if (cfg.profile === 'ramp') {
    const stepSeconds = cfg.durationSeconds / cfg.rampSteps.length;
    const stages = [];
    for (const fraction of cfg.rampSteps) {
      const target = Math.max(1, Math.round(cfg.rate * fraction));
      const rampTime = Math.max(1, Math.round(stepSeconds * 0.1));
      const holdTime = Math.max(1, Math.round(stepSeconds) - rampTime);
      stages.push({ target, duration: `${rampTime}s` }, { target, duration: `${holdTime}s` });
    }
    return {
      [name]: {
        executor: 'ramping-arrival-rate',
        startRate: Math.max(1, Math.round(cfg.rate * 0.1)),
        timeUnit: '1s',
        preAllocatedVUs: cfg.preVus,
        maxVUs: cfg.maxVus,
        stages,
        tags,
      },
    };
  }
  return {
    [name]: {
      executor: 'constant-arrival-rate',
      rate: cfg.rate,
      timeUnit: '1s',
      duration: cfg.duration,
      preAllocatedVUs: cfg.preVus,
      maxVUs: cfg.maxVus,
      tags,
    },
  };
}

/**
 * Limites informativos (o k6 NAO interrompe o teste; ao final sai com codigo 99 se algum for
 * violado). Os limites por operacao existem para que o k6 calcule as metricas por operacao.
 */
export function buildThresholds(cfg, endpoints) {
  const thresholds = {
    'http_req_failed{phase:load}': [`rate<${cfg.maxErrorRate}`],
    'http_req_duration{phase:load}': [`p(95)<${cfg.p95Ms}`],
    checks: ['rate>0.99'],
    dropped_iterations: ['count==0'],
  };
  for (const endpoint of endpoints) {
    thresholds[`http_req_duration{endpoint:${endpoint}}`] = ['max>=0'];
    thresholds[`http_req_failed{endpoint:${endpoint}}`] = ['rate>=0'];
  }
  return thresholds;
}

export function buildOptions(cfg, endpoints) {
  return {
    scenarios: buildScenarios(cfg, cfg.test),
    thresholds: buildThresholds(cfg, endpoints),
    summaryTrendStats: TREND_STATS,
    tags: { target: cfg.target, level: cfg.level, test: cfg.test, profile: cfg.profile },
  };
}

// ---------- dados ----------

function pad(value, width) {
  return String(value).padStart(width, '0');
}

/**
 * ISBN-13 sintetico "979" + 3 digitos da semente da execucao + 7 digitos do contador. Nao colide
 * com a carga padrao (que usa o prefixo "978"). Formato valido para a API (sem digito verificador).
 */
export function makeIsbn(runSeed, counter) {
  if (!Number.isInteger(counter) || counter < 0 || counter > 9999999) {
    throw new Error(`contador de iteracao fora do limite do ISBN sintetico: ${counter}`);
  }
  return `979${pad(runSeed % 1000, 3)}${pad(counter, 7)}`;
}

export function buildBook(runSeed, counter, revised) {
  const label = `${pad(runSeed % 1000, 3)}-${counter}`;
  return {
    titulo: revised ? `Livro de carga revisado ${label}` : `Livro de carga ${label}`,
    autor: 'Autor de Carga',
    isbn: makeIsbn(runSeed, counter),
    anoPublicacao: 1950 + (counter % 75),
    categoria: 'Carga',
    disponivel: !revised,
  };
}

/** Escolhe a operacao de leitura conforme os pesos (r em [0,1)). */
export function pickOperation(mix, r) {
  const total = mix.list + mix.get + mix.search;
  let x = r * total;
  if (x < mix.list) return 'list';
  x -= mix.list;
  if (x < mix.get) return 'get';
  return 'search';
}

// ---------- resumo final ----------

function values(data, name) {
  const metric = data && data.metrics ? data.metrics[name] : null;
  if (!metric) return null;
  return metric.values || metric;
}

function number(v) {
  return typeof v === 'number' && Number.isFinite(v) ? v : null;
}

function latency(v) {
  if (!v) return null;
  return {
    avg: number(v.avg), med: number(v.med), p90: number(v['p(90)']), p95: number(v['p(95)']),
    p99: number(v['p(99)']), max: number(v.max),
  };
}

function violated(data) {
  const names = [];
  const metrics = (data && data.metrics) || {};
  for (const [metricName, metric] of Object.entries(metrics)) {
    const thresholds = metric && metric.thresholds;
    if (!thresholds) continue;
    for (const [expression, result] of Object.entries(thresholds)) {
      const failed = result === false || (result && result.ok === false);
      if (failed) names.push(`${metricName}: ${expression}`);
    }
  }
  return names;
}

/**
 * Requisicoes da FASE DE CARGA (sem setup/teardown): contagem da metrica de latencia com a tag
 * phase=load; media por segundo = contagem / duracao nominal. Se a metrica nao existir, usa o total.
 */
function loadRequests(total, duration, cfg) {
  const count = duration && number(duration.count) !== null ? number(duration.count)
    : (total ? number(total.count) : null);
  if (count === null) return null;
  const seconds = cfg && cfg.durationSeconds ? cfg.durationSeconds : null;
  return { count, perSecond: seconds ? count / seconds : (total ? number(total.rate) : null) };
}

/** Resumo compacto e tolerante: campos ausentes viram null (o objeto completo tambem e gravado). */
export function compactSummary(data, endpoints, cfg) {
  const reqs = values(data, 'http_reqs');
  const failed = values(data, 'http_req_failed{phase:load}') || values(data, 'http_req_failed');
  const duration = values(data, 'http_req_duration{phase:load}') || values(data, 'http_req_duration');
  const checks = values(data, 'checks');
  const iterations = values(data, 'iterations');
  const dropped = values(data, 'dropped_iterations');
  const vusMax = values(data, 'vus_max');

  const byEndpoint = {};
  for (const endpoint of endpoints) {
    const d = values(data, `http_req_duration{endpoint:${endpoint}}`);
    const f = values(data, `http_req_failed{endpoint:${endpoint}}`);
    byEndpoint[endpoint] = {
      count: d ? number(d.count) : null,
      failedRate: f ? number(f.rate) : null,
      latencyMs: latency(d),
    };
  }

  return {
    requests: loadRequests(reqs, duration, cfg),
    failedRate: failed ? number(failed.rate) : null,
    iterations: iterations ? number(iterations.count) : null,
    droppedIterations: dropped ? number(dropped.count) : 0,
    vusMax: vusMax ? number(vusMax.max !== undefined ? vusMax.max : vusMax.value) : null,
    checksRate: checks ? number(checks.rate) : null,
    latencyMs: latency(duration),
    byEndpoint,
    thresholdsViolated: violated(data),
  };
}

function fmt(v, digits) {
  return v === null || v === undefined ? 'n/d' : Number(v).toFixed(digits === undefined ? 1 : digits);
}

function percent(v) {
  return v === null || v === undefined ? 'n/d' : `${(v * 100).toFixed(2)}%`;
}

/** Texto curto para o terminal. */
export function summaryText(summary, cfg) {
  const lines = [];
  lines.push('');
  lines.push(`=== Resumo: ${cfg.runId} ===`);
  lines.push(`alvo=${cfg.target} teste=${cfg.test} nivel=${cfg.level} perfil=${cfg.profile} `
    + `taxa=${cfg.rate}/s duracao=${cfg.duration} maxVUs=${cfg.maxVus} base=${cfg.baseUrl}`);
  const r = summary.requests;
  lines.push(`requisicoes (fase de carga): ${r ? fmt(r.count, 0) : 'n/d'} (media ${r ? fmt(r.perSecond, 1) : 'n/d'}/s) | `
    + `falhas: ${percent(summary.failedRate)} | iteracoes: ${fmt(summary.iterations, 0)} | `
    + `descartadas: ${fmt(summary.droppedIterations, 0)} | VUs max: ${fmt(summary.vusMax, 0)} | `
    + `checks: ${percent(summary.checksRate)}`);
  const l = summary.latencyMs;
  lines.push(l
    ? `latencia ms (fase de carga): media ${fmt(l.avg)} | med ${fmt(l.med)} | p90 ${fmt(l.p90)} | `
      + `p95 ${fmt(l.p95)} | p99 ${fmt(l.p99)} | max ${fmt(l.max)}`
    : 'latencia ms (fase de carga): n/d');
  for (const [endpoint, e] of Object.entries(summary.byEndpoint)) {
    const el = e.latencyMs;
    lines.push(`  ${endpoint}: ${fmt(e.count, 0)} req | falhas ${percent(e.failedRate)} | `
      + `med ${fmt(el && el.med)} | p95 ${fmt(el && el.p95)} | max ${fmt(el && el.max)}`);
  }
  lines.push(summary.thresholdsViolated.length === 0
    ? 'limites (thresholds): nenhum violado'
    : `limites (thresholds) VIOLADOS: ${summary.thresholdsViolated.join('; ')}`);
  lines.push(`arquivo: ${cfg.resultsDir}/${cfg.runId}.summary.json`);
  lines.push('');
  return lines.join('\n');
}

/** Saidas do handleSummary: texto no terminal e arquivo JSON (meta + resumo + dados completos). */
export function summaryOutputs(data, cfg, endpoints) {
  const summary = compactSummary(data, endpoints, cfg);
  // Execucao abortada (ex.: setup falhou, API parada): nao ha iteracoes, entao nao se grava
  // arquivo de resumo, para que ele nao seja confundido com uma medicao valida.
  if (!(summary.iterations > 0)) {
    return {
      stdout: `\n=== Execucao ${cfg.runId}: nenhuma iteracao executada (setup falhou ou teste `
        + 'interrompido). Nenhum resumo foi gravado. ===\n',
    };
  }
  const meta = {
    runId: cfg.runId, target: cfg.target, test: cfg.test, level: cfg.level, profile: cfg.profile,
    rate: cfg.rate, duration: cfg.duration, maxVus: cfg.maxVus, preVus: cfg.preVus,
    rampSteps: cfg.rampSteps, baseUrl: cfg.baseUrl, p95Ms: cfg.p95Ms, maxErrorRate: cfg.maxErrorRate,
    pageSize: cfg.pageSize, mix: cfg.mix, runSeed: cfg.runSeed,
  };
  return {
    stdout: summaryText(summary, cfg),
    [`${cfg.resultsDir}/${cfg.runId}.summary.json`]: JSON.stringify({ meta, summary, data }, null, 2),
  };
}
