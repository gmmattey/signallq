import { StrictMode, useCallback, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

type ProductId = "signallq" | "lagcheck" | "linka";
type Period = 7 | 30 | 90;
type MetricKey = "visits" | "storeCtaClicks" | "testStarts";

interface SourceStatus {
  available: boolean;
  reason?: string;
  state: "available" | "no_data" | "unavailable";
}

interface SeriesPoint {
  date: string;
  visits: number | null;
  storeCtaClicks: number | null;
  testStarts: number | null;
}

interface RankRow {
  label: string;
  value: number | null;
}

interface ProductMetrics {
  product: ProductId;
  metrics: Record<MetricKey, number | null>;
  series: SeriesPoint[];
  pages: RankRow[];
  referrers: RankRow[];
  availability: {
    telemetry: SourceStatus;
    webAnalytics: SourceStatus;
  };
}

interface SitesMetricsResponse {
  generatedAt: string;
  period: { key: Period; from: string; to: string };
  products: ProductMetrics[];
}

const products: Array<{ id: ProductId; name: string; accent: string }> = [
  { id: "signallq", name: "SignallQ", accent: "var(--blue)" },
  { id: "lagcheck", name: "Lagcheck", accent: "var(--amber)" },
  { id: "linka", name: "Linka", accent: "var(--violet)" },
];

const metricLabels: Record<MetricKey, string> = {
  visits: "Visitas",
  storeCtaClicks: "CTAs de loja",
  testStarts: "Testes iniciados",
};

function isProductId(value: unknown): value is ProductId {
  return value === "signallq" || value === "lagcheck" || value === "linka";
}

function integerOrNull(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
}

function status(value: unknown): SourceStatus {
  if (typeof value === "string") {
    if (value === "available") return { available: true, state: "available" };
    if (value === "no_data") return { available: false, state: "no_data", reason: "Aguardando a primeira leitura deste período." };
    return { available: false, state: "unavailable", reason: value === "not_configured" ? "Fonte ainda não configurada." : "Fonte temporariamente indisponível." };
  }
  if (!value || typeof value !== "object") return { available: false, state: "unavailable", reason: "Fonte sem resposta." };
  const candidate = value as { available?: unknown; reason?: unknown; status?: unknown; state?: unknown };
  if (typeof candidate.status === "string") return status(candidate.status);
  if (typeof candidate.state === "string") return status(candidate.state);
  return {
    available: candidate.available === true,
    reason: typeof candidate.reason === "string" ? candidate.reason : undefined,
    state: candidate.available === true ? "available" : "unavailable",
  };
}

function rows(value: unknown): RankRow[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((row) => {
    if (!row || typeof row !== "object") return [];
    const item = row as { label?: unknown; name?: unknown; path?: unknown; referrer?: unknown; referer?: unknown; value?: unknown; visits?: unknown; requests?: unknown; pageViews?: unknown; page_views?: unknown };
    const label = item.label ?? item.name ?? item.path ?? item.referrer ?? item.referer;
    const number = item.value ?? item.visits ?? item.requests ?? item.pageViews ?? item.page_views;
    return typeof label === "string" ? [{ label, value: integerOrNull(number) }] : [];
  });
}

function normalize(response: unknown, expectedPeriod: Period): SitesMetricsResponse {
  if (!response || typeof response !== "object") throw new Error("Resposta inválida do painel.");
  const payload = response as { generatedAt?: unknown; period?: unknown; products?: unknown };
  const productEntries = Array.isArray(payload.products)
    ? payload.products.map((value) => [typeof value === "object" && value ? (value as Record<string, unknown>).product : undefined, value] as const)
    : payload.products && typeof payload.products === "object" ? Object.entries(payload.products as Record<string, unknown>) : [];
  if (!productEntries.length) throw new Error("A resposta não contém produtos.");

  const parsed = productEntries.flatMap(([productId, product]) => {
    if (!isProductId(productId) || !product || typeof product !== "object") return [];
    const item = product as Record<string, unknown>;
    const rawConversions = (item.conversions ?? item.metrics ?? {}) as Record<string, unknown>;
    const rawTraffic = (item.traffic ?? {}) as Record<string, unknown>;
    const conversionDaily = dailyRows(rawConversions.daily, "webSessions");
    const trafficDaily = dailyRows(rawTraffic.daily, "visits");
    const dates = [...new Set([...conversionDaily.keys(), ...trafficDaily.keys()])].sort();
    return [{
      product: productId,
      metrics: {
        visits: integerOrNull(rawTraffic.visits ?? rawConversions.webSessions),
        storeCtaClicks: integerOrNull(rawConversions.storeCtaClicks),
        testStarts: integerOrNull(rawConversions.testStarts),
      },
      series: dates.map((date) => ({
        date,
        visits: trafficDaily.get(date)?.visits ?? conversionDaily.get(date)?.webSessions ?? null,
        storeCtaClicks: conversionDaily.get(date)?.storeCtaClicks ?? null,
        testStarts: conversionDaily.get(date)?.testStarts ?? null,
      })),
      pages: rows(rawTraffic.topPages ?? item.pages),
      referrers: rows(rawTraffic.topReferrers ?? item.referrers),
      availability: {
        telemetry: status(rawConversions.status ?? (item.availability as Record<string, unknown> | undefined)?.telemetry),
        webAnalytics: status(rawTraffic.status ?? (item.availability as Record<string, unknown> | undefined)?.webAnalytics),
      },
    } satisfies ProductMetrics];
  });

  return {
    generatedAt: typeof payload.generatedAt === "string" ? payload.generatedAt : new Date().toISOString(),
    period: parsePeriod(payload.period, expectedPeriod),
    products: parsed,
  };
}

function parsePeriod(value: unknown, fallback: Period): SitesMetricsResponse["period"] {
  if (value && typeof value === "object") {
    const period = value as { key?: unknown; from?: unknown; to?: unknown };
    const key = period.key === "7d" ? 7 : period.key === "30d" ? 30 : period.key === "90d" ? 90 : period.key;
    if (key === 7 || key === 30 || key === 90) return { key, from: typeof period.from === "string" ? period.from : "", to: typeof period.to === "string" ? period.to : "" };
  }
  return { key: fallback, from: "", to: "" };
}

interface DailyRaw { webSessions?: number | null; visits?: number | null; storeCtaClicks: number | null; testStarts: number | null }

function dailyRows(value: unknown, sessionKey: "webSessions" | "visits"): Map<string, DailyRaw> {
  const parsed = new Map<string, DailyRaw>();
  if (!Array.isArray(value)) return parsed;
  for (const row of value) {
    if (!row || typeof row !== "object") continue;
    const item = row as Record<string, unknown>;
    if (typeof item.date !== "string") continue;
    parsed.set(item.date, { [sessionKey]: integerOrNull(item[sessionKey]), storeCtaClicks: integerOrNull(item.storeCtaClicks), testStarts: integerOrNull(item.testStarts) });
  }
  return parsed;
}

function formatNumber(value: number | null): string {
  return value === null ? "Indisponível" : new Intl.NumberFormat("pt-BR").format(value);
}

function dateLabel(value: string): string {
  const date = new Date(`${value}T12:00:00Z`);
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" }).format(date);
}

function aggregate(items: ProductMetrics[], key: MetricKey): number | null {
  const values = items.map((item) => item.metrics[key]);
  return values.some((value) => value === null) ? null : values.reduce<number>((total, value) => total + (value ?? 0), 0);
}

function SourceNotice({ items }: { items: ProductMetrics[] }) {
  const unavailable = items.flatMap((item) => [item.availability.telemetry, item.availability.webAnalytics]).filter((item) => !item.available);
  if (!unavailable.length) return null;
  const hasFailure = unavailable.some((source) => source.state === "unavailable");
  return <div className="notice" role="status"><strong>{hasFailure ? "Atualização parcial" : "Coleta em andamento"}</strong><span>{unavailable[0]?.reason ?? "Uma ou mais fontes estão indisponíveis."} {hasFailure ? "O último retrato confiável continua visível abaixo." : "Os números aparecerão após as primeiras leituras."}</span></div>;
}

function MetricCard({ label, value, hint, source }: { label: string; value: number | null; hint: string; source: string }) {
  return <article className="metric-card">
    <div className="metric-card__head"><p>{label}</p><span>{source}</span></div>
    <strong className={value === null ? "unavailable" : ""}>{value === null ? "—" : formatNumber(value)}</strong>
    <small>{value === null ? `Indisponível · ${hint}` : hint}</small>
  </article>;
}

function SourceHealth({ items }: { items: ProductMetrics[] }) {
  const telemetry = items.filter((item) => item.availability.telemetry.available).length;
  const analytics = items.filter((item) => item.availability.webAnalytics.available).length;
  return <div className="source-health" aria-label="Saúde das fontes">
    <span><i className={telemetry === items.length ? "is-ready" : "is-warn"} aria-hidden="true" />Telemetria <b>{telemetry}/{items.length}</b></span>
    <span><i className={analytics === items.length ? "is-ready" : "is-warn"} aria-hidden="true" />Web Analytics <b>{analytics}/{items.length}</b></span>
  </div>;
}

function allSourcesAreAwaitingData(items: ProductMetrics[]): boolean {
  const sources = items.flatMap((item) => [item.availability.telemetry, item.availability.webAnalytics]);
  return sources.length > 0 && sources.every((source) => source.state === "no_data");
}

function CollectionState({ items }: { items: ProductMetrics[] }) {
  return <section className="collection" aria-labelledby="collection-title">
    <div className="collection__intro"><p className="eyebrow">COLETA ATIVA</p><h2 id="collection-title">Aguardando as primeiras visitas.</h2><p>Os conectores já estão prontos. Assim que cada fonte receber atividade, ela aparece aqui com o período selecionado.</p></div>
    <div className="collection__matrix" role="region" aria-label="Estado da coleta por produto e fonte"><table><thead><tr><th>Produto</th><th>Web Analytics</th><th>Telemetria</th></tr></thead><tbody>{items.map((item) => <tr key={item.product}><th scope="row">{products.find((product) => product.id === item.product)?.name}</th><td><SourceCell source={item.availability.webAnalytics} /></td><td><SourceCell source={item.availability.telemetry} /></td></tr>)}</tbody></table></div>
    <div className="collection__preview"><p className="eyebrow">QUANDO A LEITURA CHEGAR</p><ul><li><strong>Visitas e páginas</strong><span>tráfego confirmado no período</span></li><li><strong>CTAs de loja</strong><span>intenção, não instalação</span></li><li><strong>Testes iniciados</strong><span>ações que começaram no site</span></li></ul></div>
  </section>;
}

function SourceCell({ source }: { source: SourceStatus }) {
  const label = source.state === "available" ? "Recebendo dados" : source.state === "no_data" ? "Aguardando atividade" : "Indisponível";
  return <span className={`source-cell source-cell--${source.state}`}><i aria-hidden="true" />{label}</span>;
}

function LineChart({ data, metric }: { data: SeriesPoint[]; metric: MetricKey }) {
  const values = data.map((point) => point[metric]).filter((value): value is number => value !== null);
  if (!data.length || !values.length) return <p className="empty">Não há série disponível para este período.</p>;
  const max = Math.max(...values, 1);
  const width = 720;
  const height = 210;
  const padding = 20;
  const points = data.map((point, index) => {
    const value = point[metric];
    const x = padding + (index / Math.max(data.length - 1, 1)) * (width - padding * 2);
    const y = value === null ? null : height - padding - (value / max) * (height - padding * 2);
    return { x, y };
  });
  const segments: string[] = [];
  let segment: string[] = [];
  for (const point of points) {
    if (point.y === null) {
      if (segment.length) segments.push(segment.join(" "));
      segment = [];
    } else {
      segment.push(`${point.x},${point.y}`);
    }
  }
  if (segment.length) segments.push(segment.join(" "));
  const tableId = `series-values-${metric}`;
  return <div className="chart-wrap">
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`Série diária de ${metricLabels[metric].toLowerCase()}; lacunas representam dados indisponíveis.`} aria-describedby={tableId}>
      <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} className="axis" />
      {segments.map((values, index) => <polyline key={index} points={values} className="line" />)}
      {points.map((point, index) => point.y === null ? null : <circle key={index} cx={point.x} cy={point.y} r="3.5"><title>{`${dateLabel(data[index].date)}: ${formatNumber(data[index][metric])}`}</title></circle>)}
    </svg>
    <div className="chart-labels"><span>{dateLabel(data[0].date)}</span><span>{dateLabel(data[data.length - 1].date)}</span></div>
    <details className="series-values" id={tableId}><summary>Ver valores da série em tabela</summary><table><thead><tr><th>Data</th><th>{metricLabels[metric]}</th></tr></thead><tbody>{data.map((point) => <tr key={point.date}><td>{dateLabel(point.date)}</td><td>{formatNumber(point[metric])}</td></tr>)}</tbody></table></details>
  </div>;
}

function RankingTable({ title, rows: data, empty, valueLabel, source }: { title: string; rows: RankRow[]; empty: string; valueLabel: string; source: string }) {
  return <section className="panel ranking"><h2>{title}</h2><p className="table-source">{source}</p>{data.length ? <table><thead><tr><th>Origem</th><th>{valueLabel}</th></tr></thead><tbody>{data.slice(0, 6).map((row) => <tr key={row.label}><td>{row.label}</td><td>{formatNumber(row.value)}</td></tr>)}</tbody></table> : <p className="empty">{empty}</p>}</section>;
}

function sourceLabel(source: SourceStatus): string {
  return source.state === "available" ? "Disponível" : source.state === "no_data" ? "Aguardando" : "Indisponível";
}

function safeFailureMessage(response: Response, payload: unknown): string {
  if (response.status === 401 || response.status === 403) return "Seu acesso ao painel não foi autorizado.";
  const candidate = payload && typeof payload === "object"
    ? (payload as { message?: unknown; error?: unknown; detail?: unknown; reason?: unknown })
    : {};
  const detail = [candidate.message, candidate.error, candidate.detail, candidate.reason]
    .find((value): value is string => typeof value === "string" && value.trim().length > 0)
    ?.replace(/\s+/g, " ").trim().slice(0, 180);
  return detail ? `Não foi possível atualizar os dados: ${detail}` : "Não foi possível atualizar os dados agora.";
}

function App() {
  const [period, setPeriod] = useState<Period>(30);
  const [selected, setSelected] = useState<ProductId | "all">("all");
  const [metric, setMetric] = useState<MetricKey>("visits");
  const [data, setData] = useState<SitesMetricsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const response = await fetch(`/admin/metrics/sites?period=${period}d`, { credentials: "same-origin", headers: { Accept: "application/json" } });
      const payload: unknown = response.headers.get("content-type")?.includes("application/json") ? await response.json().catch(() => null) : null;
      if (!response.ok) throw new Error(safeFailureMessage(response, payload));
      setData(normalize(payload, period));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível atualizar os dados.");
    } finally { setLoading(false); }
  }, [period]);

  useEffect(() => { void load(); }, [load]);

  const visible = useMemo(() => data?.products.filter((item) => selected === "all" || item.product === selected) ?? [], [data, selected]);
  const dashboardSeries = useMemo(() => {
    const dates = [...new Set(visible.flatMap((item) => item.series.map((point) => point.date)))].sort();
    return dates.map((date) => ({
      date,
      visits: sumSeries(visible, date, "visits"),
      storeCtaClicks: sumSeries(visible, date, "storeCtaClicks"),
      testStarts: sumSeries(visible, date, "testStarts"),
    }));
  }, [visible]);
  const ranking = useMemo(() => mergeRows(visible.flatMap((item) => item.pages)), [visible]);
  const referrers = useMemo(() => mergeRows(visible.flatMap((item) => item.referrers)), [visible]);

  const isTotalFailure = !data && Boolean(error);
  const isAwaitingFirstData = allSourcesAreAwaitingData(visible);
  return <div className="shell">
    <main id="sites">
      <header className="topbar">
        <a className="brand" href="#sites" aria-label="SignallQ Admin, Sites"><span>signallQ</span><small>ADMIN / SITES</small></a>
        <div className="toolbar">
          <div className="periods" role="group" aria-label="Período">{([7, 30, 90] as Period[]).map((value) => <button key={value} type="button" className={period === value ? "selected" : ""} aria-pressed={period === value} onClick={() => setPeriod(value)}>{value}d</button>)}</div>
          <button className="refresh" type="button" onClick={() => void load()} disabled={loading}>{loading ? "Atualizando…" : "Atualizar"}</button>
        </div>
      </header>
      <section className="hero" aria-labelledby="page-title">
        <div><p className="eyebrow">ADMINISTRAÇÃO · DADOS AGREGADOS</p><h1 id="page-title">Visão dos sites</h1><p className="subtle">Tráfego, intenção e testes — sem chamar clique de instalação.</p></div>
        {data && <div className="freshness"><span>Última leitura</span><strong>{new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(data.generatedAt))}</strong><SourceHealth items={visible} /></div>}
      </section>
      <div className="filters" role="group" aria-label="Filtrar produto"><span>Produto</span>{["all", ...products.map((item) => item.id)].map((item) => <button type="button" key={item} className={selected === item ? "selected" : ""} aria-pressed={selected === item} onClick={() => setSelected(item as ProductId | "all")}>{item === "all" ? "Todos" : products.find((product) => product.id === item)?.name}</button>)}</div>
      {loading && !data ? <section className="state" aria-live="polite"><span className="spinner" />Carregando a leitura mais recente…</section> : isTotalFailure ? <section className="state error" role="alert"><p className="eyebrow">ATUALIZAÇÃO INTERROMPIDA</p><h2>O painel não respondeu agora.</h2><p>{error}</p><button type="button" onClick={() => void load()}>Tentar novamente</button></section> : !data || !visible.length ? <section className="state"><h2>Ainda não há leitura para mostrar.</h2><p>Os eventos aparecerão quando as fontes estiverem configuradas e começarem a receber coleta.</p></section> : isAwaitingFirstData ? <CollectionState items={visible} /> : <>
        {error && <div className="notice" role="status"><strong>Atualização parcial</strong><span>{error} O último retrato confiável continua visível abaixo.</span></div>}
        <SourceNotice items={visible} />
        <section className="metrics" aria-label="Resumo do ecossistema"><MetricCard label="Visitas" value={aggregate(visible, "visits")} hint="Sessões web medidas" source="Web Analytics" /><MetricCard label="CTAs de loja" value={aggregate(visible, "storeCtaClicks")} hint="Clique não confirma instalação" source="Telemetria" /><MetricCard label="Testes iniciados" value={aggregate(visible, "testStarts")} hint="Ação iniciada no site" source="Telemetria" /></section>
        <section className="panel comparison-panel"><div className="panel-heading"><div><p className="eyebrow">ECOSSISTEMA</p><h2>Comparação por produto</h2><p>Somente dados que as fontes confirmaram neste período.</p></div></div><div className="comparison" role="list">{visible.map((item) => <article key={item.product} role="listitem" className="product-card" style={{ "--accent": products.find((product) => product.id === item.product)?.accent } as React.CSSProperties}><div className="product-card__top"><h3>{products.find((product) => product.id === item.product)?.name}</h3><span className="product-accent" aria-hidden="true" /></div><dl><div><dt>Visitas</dt><dd>{item.metrics.visits === null ? "—" : formatNumber(item.metrics.visits)}</dd></div><div><dt>CTA loja</dt><dd>{item.metrics.storeCtaClicks === null ? "—" : formatNumber(item.metrics.storeCtaClicks)}</dd></div><div><dt>Teste</dt><dd>{item.metrics.testStarts === null ? "—" : formatNumber(item.metrics.testStarts)}</dd></div></dl><div className="source-badges"><span className={item.availability.webAnalytics.available ? "available" : "unavailable"}>Web Analytics · {sourceLabel(item.availability.webAnalytics)}</span><span className={item.availability.telemetry.available ? "available" : "unavailable"}>Telemetria · {sourceLabel(item.availability.telemetry)}</span></div></article>)}</div></section>
        <section className="content-grid"><section className="panel series-panel"><div className="panel-heading"><div><p className="eyebrow">MOVIMENTO</p><h2>Evolução diária</h2><p>Soma diária das fontes disponíveis.</p></div><label className="metric-select">Métrica<select value={metric} onChange={(event) => setMetric(event.target.value as MetricKey)}><option value="visits">Visitas</option><option value="storeCtaClicks">CTAs de loja</option><option value="testStarts">Testes iniciados</option></select></label></div><LineChart data={dashboardSeries} metric={metric} /></section>
          <section className="panel funnel"><div><p className="eyebrow">INTENÇÃO</p><h2>Funil dos sites</h2><p>O clique na loja não confirma uma instalação.</p></div><ol>{(["visits", "storeCtaClicks", "testStarts"] as MetricKey[]).map((key, index) => <li key={key}><span>{index + 1}</span><div><strong>{metricLabels[key]}</strong><b>{aggregate(visible, key) === null ? "—" : formatNumber(aggregate(visible, key))}</b></div></li>)}</ol></section></section>
        <section className="data-grid"><RankingTable title="Páginas mais vistas" rows={ranking} empty="A telemetria de visualizações ainda não está disponível." valueLabel="Visualizações" source="Eventos screen_view coletados pelo site." /><RankingTable title="Origens de tráfego" rows={referrers} empty="A fonte de origens HTTP ainda não está disponível." valueLabel="Requisições" source="Requisições HTTP; não equivale a visitas." /></section>
      </>}
    </main>
  </div>;
}

function sumSeries(items: ProductMetrics[], date: string, key: MetricKey): number | null {
  const values = items.map((item) => item.series.find((point) => point.date === date)?.[key] ?? null);
  return values.some((value) => value === null) ? null : values.reduce<number>((sum, value) => sum + (value ?? 0), 0);
}

function mergeRows(rowsToMerge: RankRow[]): RankRow[] {
  const totals = new Map<string, number | null>();
  for (const row of rowsToMerge) {
    const previous = totals.get(row.label);
    totals.set(row.label, previous === undefined ? row.value : previous === null || row.value === null ? null : previous + row.value);
  }
  return [...totals].map(([label, value]) => ({ label, value })).sort((a, b) => (b.value ?? -1) - (a.value ?? -1));
}

createRoot(document.getElementById("root")!).render(<StrictMode><App /></StrictMode>);
