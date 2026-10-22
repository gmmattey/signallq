import { StrictMode, useCallback, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

type ProductId = "signallq" | "lagcheck" | "linka";
type Period = 7 | 30 | 90;
type MetricKey = "visits" | "storeCtaClicks" | "testStarts";

interface SourceStatus {
  available: boolean;
  reason?: string;
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
    return value === "available"
      ? { available: true }
      : { available: false, reason: value === "not_configured" ? "Fonte ainda não configurada." : value === "no_data" ? "Ainda não há dados para este período." : "Fonte temporariamente indisponível." };
  }
  if (!value || typeof value !== "object") return { available: false, reason: "Fonte sem resposta." };
  const candidate = value as { available?: unknown; reason?: unknown; status?: unknown };
  if (typeof candidate.status === "string") return status(candidate.status);
  return {
    available: candidate.available === true,
    reason: typeof candidate.reason === "string" ? candidate.reason : undefined,
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
  return <p className="notice" role="status">Dados parciais: {unavailable[0]?.reason ?? "uma ou mais fontes estão indisponíveis."}</p>;
}

function MetricCard({ label, value, hint }: { label: string; value: number | null; hint: string }) {
  return <article className="metric-card">
    <p>{label}</p>
    <strong className={value === null ? "unavailable" : ""}>{formatNumber(value)}</strong>
    <small>{hint}</small>
  </article>;
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
      const response = await fetch(`/admin/metrics/sites?period=${period}`, { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? "Seu acesso ao painel não foi autorizado." : "Não foi possível atualizar os dados.");
      setData(normalize(await response.json(), period));
    } catch (cause) {
      setData(null); setError(cause instanceof Error ? cause.message : "Não foi possível atualizar os dados.");
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

  return <div className="shell">
    <aside><a className="brand" href="/" aria-label="SignallQ admin"><span>signallQ</span><small>ADMIN</small></a><nav aria-label="Navegação principal"><a className="active" href="#sites" aria-current="page">Sites</a></nav><p className="side-note">Métricas de crescimento e conversão web.</p></aside>
    <main id="sites">
      <header className="topbar"><div><p className="eyebrow">ECOSSISTEMA</p><h1>Sites</h1><p className="subtle">Acompanhe tráfego e intenção de conversão sem confundir clique com instalação.</p></div><div className="toolbar"><label>Período<select value={period} onChange={(event) => setPeriod(Number(event.target.value) as Period)} aria-label="Período"><option value={7}>7 dias</option><option value={30}>30 dias</option><option value={90}>90 dias</option></select></label><button onClick={() => void load()} disabled={loading}>{loading ? "Atualizando…" : "Atualizar"}</button></div></header>
      <div className="filters" role="group" aria-label="Filtrar produto"><span>Produto</span>{["all", ...products.map((item) => item.id)].map((item) => <button key={item} className={selected === item ? "selected" : ""} aria-pressed={selected === item} onClick={() => setSelected(item as ProductId | "all")}>{item === "all" ? "Todos" : products.find((product) => product.id === item)?.name}</button>)}</div>
      {loading && !data ? <section className="state" aria-live="polite"><span className="spinner" />Carregando métricas…</section> : error ? <section className="state error" role="alert"><h2>Não foi possível carregar o painel</h2><p>{error}</p><button onClick={() => void load()}>Tentar novamente</button></section> : !data || !visible.length ? <section className="state"><h2>Nenhum dado disponível</h2><p>Os eventos aparecerão aqui quando as fontes estiverem configuradas e receberem coleta.</p></section> : <>
        <SourceNotice items={visible} />
        <section className="metrics" aria-label="Resumo do ecossistema"><MetricCard label="Visitas" value={aggregate(visible, "visits")} hint="Sessões web medidas" /><MetricCard label="CTAs de loja" value={aggregate(visible, "storeCtaClicks")} hint="Clique não confirma instalação" /><MetricCard label="Testes iniciados" value={aggregate(visible, "testStarts")} hint="Ação iniciada no site" /></section>
        <section className="panel"><div className="panel-heading"><div><h2>Comparação por produto</h2><p>Dados disponíveis no período selecionado.</p></div></div><div className="comparison" role="list">{visible.map((item) => <article key={item.product} role="listitem" className="product-card" style={{ "--accent": products.find((product) => product.id === item.product)?.accent } as React.CSSProperties}><h3>{products.find((product) => product.id === item.product)?.name}</h3><dl><div><dt>Visitas</dt><dd>{formatNumber(item.metrics.visits)}</dd></div><div><dt>CTA loja</dt><dd>{formatNumber(item.metrics.storeCtaClicks)}</dd></div><div><dt>Teste</dt><dd>{formatNumber(item.metrics.testStarts)}</dd></div></dl><p className={item.availability.webAnalytics.available && item.availability.telemetry.available ? "source-ok" : "source-pending"}>{item.availability.webAnalytics.available && item.availability.telemetry.available ? "Fontes disponíveis" : "Dados parcialmente disponíveis"}</p></article>)}</div></section>
        <section className="panel"><div className="panel-heading"><div><h2>Evolução diária</h2><p>Dados agregados por dia.</p></div><label className="metric-select">Métrica<select value={metric} onChange={(event) => setMetric(event.target.value as MetricKey)}><option value="visits">Visitas</option><option value="storeCtaClicks">CTAs de loja</option><option value="testStarts">Testes iniciados</option></select></label></div><LineChart data={dashboardSeries} metric={metric} /></section>
        <section className="data-grid"><RankingTable title="Visualizações (telemetria)" rows={ranking} empty="A telemetria de visualizações ainda não está disponível." valueLabel="Visualizações" source="Eventos screen_view coletados pelo site; não são pageviews estimados por requisições HTTP." /><RankingTable title="Origens por requisições HTTP" rows={referrers} empty="A fonte de origens HTTP ainda não está disponível." valueLabel="Requisições" source="Cloudflare httpRequestsAdaptiveGroups; cada valor é uma requisição HTTP, não uma visita." /></section>
        <section className="panel funnel"><div><h2>Funil de intenção</h2><p>O clique na loja é intenção; a instalação acontece fora deste painel.</p></div><ol>{(["visits", "storeCtaClicks", "testStarts"] as MetricKey[]).map((key, index) => <li key={key}><span>{index + 1}</span><div><strong>{metricLabels[key]}</strong><b>{formatNumber(aggregate(visible, key))}</b></div></li>)}</ol></section>
        <p className="updated">Atualizado em {new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(data.generatedAt))}.</p>
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
