"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { type FeatureData, getRelatedFeatures } from "@/lib/featuresData";

const BASE = "https://speed.cloudflare.com";

interface Metric {
  l: string;
  v: string | number;
  u: string;
}

export function FeatureBody({ feature }: { feature: FeatureData }) {
  const f = feature;
  const isApp = f.tool === "app";
  const [phase, setPhase] = useState<"idle" | "running" | "done" | "error">("idle");
  const [label, setLabel] = useState("");
  const [live, setLive] = useState(0);
  const [unit, setUnit] = useState("ms");
  const [pct, setPct] = useState(0);
  const [metrics, setMetrics] = useState<Metric[]>([]);
  const [verdict, setVerdict] = useState("");
  const [sub, setSub] = useState("");
  const [barVisible, setBarVisible] = useState(false);
  const [tick, setTick] = useState(0);

  const runRef = useRef(0);

  // Animated tick for visualizers
  useEffect(() => {
    const timer = setInterval(() => setTick((t) => t + 1), 70);
    const onScroll = () => {
      const y = window.scrollY;
      const h = document.documentElement.scrollHeight - window.innerHeight;
      setBarVisible(y > 500 && y < h - 400);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      clearInterval(timer);
      window.removeEventListener("scroll", onScroll);
    };
  }, []);

  const fmt = (v: number) => (v >= 100 ? String(Math.round(v)) : v.toFixed(1));

  const ping1 = async (ms = 2500): Promise<number | null> => {
    const c = new AbortController();
    const t = setTimeout(() => c.abort(), ms);
    const s = performance.now();
    try {
      await fetch(`${BASE}/__down?bytes=0&r=${Math.random()}`, {
        cache: "no-store",
        signal: c.signal,
      });
      clearTimeout(t);
      return performance.now() - s;
    } catch {
      clearTimeout(t);
      return null;
    }
  };

  const runTool = async () => {
    const id = ++runRef.current;
    const ok = () => runRef.current === id;

    setPhase("running");
    setLive(0);
    setPct(3);
    setUnit("ms");
    setMetrics([]);
    setLabel("Medindo…");

    try {
      if (f.tool === "ping" || f.tool === "jitter") {
        const n = f.tool === "ping" ? 10 : 20;
        const s: number[] = [];
        let lost = 0;
        setLabel(f.tool === "ping" ? "Medindo latência…" : "Medindo estabilidade…");

        for (let i = 0; i < n; i++) {
          const v = await ping1(f.tool === "ping" ? 4000 : 1500);
          if (!ok()) return;
          if (v == null) lost++;
          else s.push(v);
          setLive(s.length ? Math.round(s[s.length - 1]) : 0);
          setPct(Math.round(((i + 1) / n) * 100));
        }

        if (!s.length) throw new Error("No responses");

        const avg = s.reduce((a, b) => a + b, 0) / s.length;
        const min = Math.min(...s);
        const max = Math.max(...s);

        if (f.tool === "ping") {
          const v =
            avg < 30
              ? ["Seu ping está ótimo.", "Resposta rápida. Sem atraso perceptível em jogos e chamadas."]
              : avg < 60
              ? ["Seu ping está bom.", "Serve para quase tudo, inclusive jogos."]
              : avg < 100
              ? ["Seu ping está razoável.", "Jogos rápidos e chamadas podem sentir atraso."]
              : ["Seu ping está alto.", "Atraso visível. Tente cabo ou chegue mais perto do roteador."];
          setVerdict(v[0]);
          setSub(v[1]);
          setMetrics([
            { l: "Média", v: Math.round(avg), u: "ms" },
            { l: "Menor", v: Math.round(min), u: "ms" },
            { l: "Pior", v: Math.round(max), u: "ms" },
          ]);
        } else {
          let j = 0;
          for (let i = 1; i < s.length; i++) j += Math.abs(s[i] - s[i - 1]);
          j = s.length > 1 ? j / (s.length - 1) : 0;
          const loss = Math.round((lost / n) * 100);
          const v =
            j < 10 && loss === 0
              ? ["Conexão estável.", "Pouca oscilação e nenhuma perda. Bom para chamadas e jogos."]
              : j < 30 && loss <= 2
              ? ["Estabilidade aceitável.", "Há alguma oscilação. Chamadas longas podem engasgar."]
              : ["Conexão instável.", "Oscilação ou perda altas. Áudio picotado e saltos nos jogos são esperados."];
          setVerdict(v[0]);
          setSub(v[1]);
          setMetrics([
            { l: "Latência média", v: Math.round(avg), u: "ms" },
            { l: "Jitter", v: fmt(j), u: "ms" },
            { l: "Perda de pacotes", v: loss, u: "%" },
          ]);
        }
        setPhase("done");
      } else if (f.tool === "speed") {
        setLabel("Medindo latência…");
        const t: number[] = [];
        for (let i = 0; i < 4; i++) {
          const v = await ping1(2000);
          if (!ok()) return;
          if (v != null) t.push(v);
        }
        if (!t.length) throw new Error("Latency failed");

        setLabel("Medindo download…");
        setUnit("Mbps");
        setLive(0);
        setPct(15);

        const t0 = performance.now();
        let bytes = 0;
        let dl = 0;
        for (const sz of [2e6, 8e6, 20e6]) {
          const r = await fetch(`${BASE}/__down?bytes=${sz}&r=${Math.random()}`, { cache: "no-store" });
          const rd = r.body?.getReader();
          if (rd) {
            for (;;) {
              const { done, value } = await rd.read();
              if (done) break;
              bytes += value.length;
              const el = (performance.now() - t0) / 1000;
              dl = (bytes * 8) / el / 1e6;
              if (!ok()) {
                rd.cancel();
                return;
              }
              setLive(dl);
              setPct(15 + Math.min(45, el * 8));
            }
          }
          if ((performance.now() - t0) / 1000 > 5) break;
        }

        setLabel("Medindo upload…");
        setLive(0);
        setPct(65);
        const blob = new Uint8Array(2e6);
        const u0 = performance.now();
        let ub = 0;
        let ul = 0;
        for (let i = 0; i < 5; i++) {
          await fetch(`${BASE}/__up`, { method: "POST", body: blob, cache: "no-store" });
          ub += blob.length;
          ul = (ub * 8) / ((performance.now() - u0) / 1000) / 1e6;
          if (!ok()) return;
          setLive(ul);
          setPct(65 + (i + 1) * 7);
          if ((performance.now() - u0) / 1000 > 5) break;
        }

        const v =
          dl >= 100
            ? ["Sua internet está ótima.", "Rápida o bastante para vários aparelhos ao mesmo tempo."]
            : dl >= 25
            ? ["Sua internet está boa.", "Atende bem o dia a dia: vídeo, trabalho e redes sociais."]
            : dl >= 8
            ? ["Sua internet dá para o básico.", "Funciona para o essencial, mas pode ficar apertada."]
            : ["Internet lenta.", "Dá para mensagens e páginas simples. Vídeos e chamadas podem travar."];

        setVerdict(v[0]);
        setSub(v[1]);
        setPct(100);
        setMetrics([
          { l: "Download", v: fmt(dl), u: "Mbps" },
          { l: "Upload", v: fmt(ul), u: "Mbps" },
          { l: "Latência", v: Math.round(Math.min(...t)), u: "ms" },
        ]);
        setPhase("done");
      } else if (f.tool === "dns") {
        const rs = [
          ["Cloudflare 1.1.1.1", "https://cloudflare-dns.com/dns-query?name=google.com&type=A"],
          ["Google 8.8.8.8", "https://dns.google/resolve?name=google.com&type=A"],
          ["Quad9 9.9.9.9", "https://dns.quad9.net:5053/dns-query?name=google.com&type=A"],
        ];
        setLabel("Consultando servidores de DNS…");
        const out: { l: string; n: number | null }[] = [];

        for (let k = 0; k < rs.length; k++) {
          const tArr: number[] = [];
          for (let i = 0; i < 3; i++) {
            const c = new AbortController();
            const to = setTimeout(() => c.abort(), 3000);
            const s = performance.now();
            try {
              await fetch(rs[k][1], {
                headers: { accept: "application/dns-json" },
                cache: "no-store",
                signal: c.signal,
              });
              tArr.push(performance.now() - s);
            } catch {
              // ignore
            }
            clearTimeout(to);
            if (!ok()) return;
          }
          tArr.sort((a, b) => a - b);
          out.push({ l: rs[k][0], n: tArr.length ? tArr[Math.floor(tArr.length / 2)] : null });
          setPct(Math.round(((k + 1) / rs.length) * 100));
        }

        const good = out.filter((o) => o.n != null).sort((a, b) => (a.n as number) - (b.n as number));
        if (!good.length) throw new Error("DNS queries failed");

        setVerdict(`${good[0].l.split(" ")[0]} respondeu mais rápido.`);
        setSub(
          (good[0].n as number) < 50
            ? "Tempo bom. Trocar o DNS traria pouca diferença."
            : "Se as páginas demoram a abrir, trocar o DNS pode ajudar."
        );
        setMetrics(out.map((o) => ({ l: o.l, v: o.n == null ? "—" : Math.round(o.n), u: o.n == null ? "" : "ms" })));
        setPhase("done");
      } else if (f.tool === "mobile") {
        const nav = typeof navigator !== "undefined" ? (navigator as unknown as { connection?: { effectiveType?: string; downlink?: number; rtt?: number; saveData?: boolean } }) : {};
        const c = nav.connection;
        if (!c) {
          setVerdict("Seu navegador não informa a rede.");
          setSub("Chrome no Android mostra uma estimativa. Para ler o sinal real em dBm, use o app.");
          setMetrics([]);
        } else {
          const tp: Record<string, string> = { "slow-2g": "2G lento", "2g": "2G", "3g": "3G", "4g": "4G ou melhor" };
          setVerdict(`Tipo de conexão: ${tp[c.effectiveType || ""] || c.effectiveType || "desconhecido"}.`);
          setSub("Estimativa feita pelo navegador. Não é o sinal em dBm da torre.");
          setMetrics([
            { l: "Velocidade estimada", v: c.downlink != null ? c.downlink : "—", u: "Mbps" },
            { l: "Tempo de resposta", v: c.rtt != null ? c.rtt : "—", u: "ms" },
            { l: "Economia de dados", v: c.saveData ? "Ligada" : "Desligada", u: "" },
          ]);
        }
        setPhase("done");
      }
    } catch {
      if (ok()) setPhase("error");
    }
  };

  const cancelTool = () => {
    runRef.current++;
    setPhase("idle");
  };

  const btnLabel =
    ({
      app: "",
      ping: "Medir meu ping",
      speed: "Iniciar teste",
      dns: "Comparar DNS",
      jitter: "Medir estabilidade",
      mobile: "Ver o que meu navegador informa",
    } as Record<string, string>)[f.tool] || "";

  const related = getRelatedFeatures(f.slug);

  const steps = !isApp
    ? [
        { n: "1", t: "Toque em iniciar", d: "Sem instalar nada. Roda no navegador." },
        { n: "2", t: "Aguarde alguns segundos", d: "Medimos várias vezes para evitar um número fora da curva." },
        { n: "3", t: "Receba o veredito", d: "Em palavras, não só em números." },
      ]
    : [
        { n: "1", t: "Baixe o app", d: "Grátis e sem cadastro, na Play Store." },
        { n: "2", t: "Abra Ferramentas", d: "O recurso está a um toque de distância." },
        { n: "3", t: "Veja a resposta", d: "A conclusão vem primeiro, e o passo seguinte logo depois." },
      ];

  // Visualizer calculations based on tick
  const sn = Math.sin;
  const noisy = f.slug === "jitter";
  const bars = Array.from({ length: 28 }, (_, i) => ({
    h: Math.round(18 + 62 * (0.5 + 0.5 * sn(i * 0.45 + tick * 0.14)) * (noisy ? 0.55 + 0.45 * Math.abs(sn(i * 7.3 + tick * 0.35)) : 1)),
  }));
  const msNum = Math.round(26 + 12 * sn(tick * 0.09) + (noisy ? 6 * sn(tick * 1.3) : 0));
  const dnsRows = ["Cloudflare", "Google", "Quad9"].map((n, k) => {
    const ms = Math.round(18 + 14 * (0.5 + 0.5 * sn(tick * 0.07 + k * 2.1)) + k * 4);
    return { n, ms, w: Math.round((ms / 48) * 100) };
  });
  const nSig = 1 + Math.floor(((sn(tick * 0.05) + 1) / 2) * 3.99);
  const sigBars = [1, 2, 3, 4, 5].map((i) => ({ h: 20 + i * 16, c: i <= nSig ? "#5B21D6" : "#E7E0EC" }));
  const sigDb = [-86, -76, -66, -58, -50][nSig - 1];
  const sigTxt = ["Fraco", "Fraco", "Bom", "Muito bom", "Excelente"][nSig - 1];
  const chs = [30, 45, 25, 35, 50, 85, 40, 30, 45, 55, 20].map((b, i) => ({
    l: i + 1,
    h: Math.round(b + 9 * sn(tick * 0.08 + i)),
    c: i === 5 ? "#BA1A1A" : i === 10 ? "#146C2E" : "#CAC4D0",
  }));
  const ph = tick % 140;
  const score = Math.min(82, Math.round(ph * 2.2));
  const pdfLines = [88, 70, 95, 60, 80].map((w, i) => (ph > i * 8 ? w : 0));
  const dn = 1 + Math.floor((tick / 14) % 4);
  const devs = [
    ["Pixel 8", "Celular"],
    ["Smart TV", "TV"],
    ["Notebook", "Computador"],
    ["Desconhecido", "Verificar"],
  ].map((d, i) => ({ n: d[0], k: d[1], o: i < dn ? 1 : 0.12, c: i === 3 ? "#8A5000" : "#1C1B1F" }));
  const actRoom = Math.floor(tick / 22) % 4;
  const rooms = [
    ["Sala", "Bom", "#B6F2BE"],
    ["Cozinha", "Bom", "#B6F2BE"],
    ["Escritório", "Médio", "#FFDDB3"],
    ["Quarto", "Fraco", "#FFB4AB"],
  ].map((r, i) => ({
    n: r[0],
    k: r[1],
    bg: r[2],
    b: i === actRoom ? "2px solid #210A5C" : "2px solid transparent",
    s: i === actRoom ? 1.04 : 1,
  }));

  return (
    <div className="font-sans leading-[normal] text-[#1C1B1F]">
      {/* Hero */}
      <div className={isApp ? "bg-[#0B0D10] text-[#F5F2F7]" : "bg-white text-[#1C1B1F]"}>
        <section
          className={`mx-auto grid items-center px-6 ${
            isApp
              ? "max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(min(340px,100%),1fr))] gap-14 pb-20 pt-[72px]"
              : "max-w-[768px] grid-cols-1 gap-10 pb-16 pt-14 text-center"
          }`}
        >
          <div className={isApp ? "text-left" : "text-center"}>
            <Link
              href="/funcionalidades"
              className={`inline-flex h-8 items-center rounded-lg border px-3 text-[14px] font-medium no-underline transition-colors ${
                isApp
                  ? "border-[#3A3446] text-[#CAC4D0] hover:text-white"
                  : "border-[#CAC4D0] text-[#49454F] hover:border-[#5B21D6] hover:text-[#5B21D6]"
              }`}
            >
              ← Funcionalidades
            </Link>
            <div className={`mt-7 text-[14px] font-semibold ${isApp ? "text-[#C9B6FF]" : "text-[#5B21D6]"}`}>
              {f.eyebrow}
            </div>
            <h1
              className={`m-0 mt-3 text-balance text-[clamp(38px,5.4vw,64px)] font-bold leading-[1.03] tracking-[-1.6px] ${
                isApp ? "text-[#F5F2F7]" : "text-[#1C1B1F]"
              }`}
            >
              {f.h1}
            </h1>
            <p
              className={`mt-6 text-pretty text-[20px] leading-[30px] ${
                isApp ? "text-[#CAC4D0]" : "mx-auto text-[#49454F]"
              } max-w-[30em]`}
            >
              {f.intro}
            </p>

            {isApp && (
              <div className="mt-8 flex flex-wrap gap-3">
                <Link
                  href="/#baixar"
                  className="rounded-full bg-[#EAE0FF] px-[30px] py-[17px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white"
                >
                  Baixar no Google Play
                </Link>
              </div>
            )}

            <div
              className={`mt-5 flex flex-wrap gap-x-5 gap-y-2 text-[14px] ${
                isApp ? "justify-start text-[#CAC4D0]" : "justify-center text-[#49454F]"
              }`}
            >
              <span>✓ Grátis</span>
              <span>✓ Sem cadastro</span>
              <span>✓ Resultado em palavras</span>
            </div>
          </div>

          {/* Interactive Card / Illustration */}
          <div
            id="demo"
            className="w-full max-w-[460px] justify-self-center rounded-[28px] border border-[#E7E0EC] bg-white p-7 text-[#1C1B1F] shadow-[0_30px_60px_-28px_rgba(33,10,92,0.35)]"
          >
            {/* Visualizer Header */}
            {(!isApp ? phase === "idle" : true) && (
              <div className="mb-8">
                {/* Ping / Jitter bars */}
                {(f.slug === "ping" || f.slug === "jitter") && (
                  <div>
                    <div className="flex items-baseline justify-center gap-2">
                      <span className="font-mono text-[72px] font-bold leading-none tracking-[-3px]">{msNum}</span>
                      <span className="text-[18px] font-medium text-[#49454F]">ms</span>
                    </div>
                    <div className="mt-5 flex h-[90px] items-end gap-1">
                      {bars.map((b, i) => (
                        <div key={i} style={{ height: `${b.h}%` }} className="flex-1 rounded bg-[#5B21D6] opacity-85" />
                      ))}
                    </div>
                  </div>
                )}

                {/* DNS */}
                {f.slug === "dns" && (
                  <div className="flex flex-col gap-4">
                    {dnsRows.map((r) => (
                      <div key={r.n}>
                        <div className="flex justify-between text-[15px] font-medium">
                          <span>{r.n}</span>
                          <span className="font-mono">{r.ms} ms</span>
                        </div>
                        <div className="mt-[6px] h-[10px] overflow-hidden rounded-full bg-[#F3EEFA]">
                          <div style={{ width: `${r.w}%` }} className="h-full rounded-full bg-[#5B21D6]" />
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Speedometer gauge for velocidade */}
                {f.slug === "velocidade" && (
                  <div className="flex flex-col items-center">
                    <div className="font-mono text-[64px] font-bold leading-none tracking-[-2px] text-[#5B21D6]">
                      {Math.round(4 + ((sn(tick * 0.06) + 1) / 2) * 56)}
                    </div>
                    <div className="mt-1 text-[16px] font-semibold text-[#49454F]">Mbps · Download</div>
                  </div>
                )}

                {/* Wi-Fi Signal dBm */}
                {f.slug === "wifi" && (
                  <div className="text-center">
                    <div className="flex h-[110px] items-end justify-center gap-[10px]">
                      {sigBars.map((s, i) => (
                        <div key={i} style={{ height: `${s.h}%`, background: s.c }} className="w-[26px] rounded-md" />
                      ))}
                    </div>
                    <div className="mt-4 font-mono text-[48px] font-bold leading-none tracking-[-1.5px]">
                      {sigDb}
                      <span className="text-[18px] font-medium text-[#49454F]"> dBm</span>
                    </div>
                    <div className="mt-[6px] text-[16px] font-semibold text-[#5B21D6]">{sigTxt}</div>
                  </div>
                )}

                {/* Canais Wi-Fi */}
                {f.slug === "canais" && (
                  <div>
                    <div className="flex h-[120px] items-end gap-[6px]">
                      {chs.map((c, i) => (
                        <div key={i} style={{ height: `${c.h}%`, background: c.c }} className="flex-1 rounded-t-md" />
                      ))}
                    </div>
                    <div className="mt-[6px] flex gap-[6px] text-center text-[12px] text-[#49454F]">
                      {chs.map((c, i) => (
                        <div key={i} className="flex-1">
                          {c.l}
                        </div>
                      ))}
                    </div>
                    <div className="mt-[14px] flex justify-between text-[14px] font-semibold">
                      <span className="text-[#BA1A1A]">Canal 6 · congestionado</span>
                      <span className="text-[#146C2E]">Canal 11 · livre</span>
                    </div>
                  </div>
                )}

                {/* Relatorio PDF */}
                {f.slug === "relatorio" && (
                  <div className="flex justify-center">
                    <div className="w-[240px] rounded-xl border border-[#E7E0EC] bg-white p-4 shadow-lg">
                      <div className="flex items-center justify-between text-[13px] font-bold">
                        <span>Relatório</span>
                        <span className="rounded-full bg-[#EAE0FF] px-2 py-0.5 text-[11px] text-[#210A5C]">PDF</span>
                      </div>
                      <div className="mt-3 flex items-baseline gap-1 font-mono text-[40px] font-bold text-[#146C2E]">
                        {score}
                        <span className="text-[12px] text-[#49454F]">score</span>
                      </div>
                      <div className="mt-3 flex flex-col gap-2">
                        {pdfLines.map((w, i) => (
                          <div key={i} className="h-[6px] rounded bg-[#F3EEFA]">
                            <div style={{ width: `${w}%` }} className="h-full rounded bg-[#CAC4D0] transition-all" />
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                {/* Dispositivos na rede */}
                {f.slug === "dispositivos" && (
                  <div>
                    <div className="mb-2 text-[13px] font-medium text-[#49454F]">Procurando aparelhos…</div>
                    {devs.map((d, i) => (
                      <div
                        key={i}
                        style={{ opacity: d.o }}
                        className="flex justify-between border-b border-[#F3EEFA] py-3 text-[15px] transition-opacity"
                      >
                        <span>{d.n}</span>
                        <b style={{ color: d.c }}>{d.k}</b>
                      </div>
                    ))}
                  </div>
                )}

                {/* Mapa de calor */}
                {f.slug === "mapa" && (
                  <div className="grid grid-cols-2 gap-2">
                    {rooms.map((r, i) => (
                      <div
                        key={i}
                        style={{ background: r.bg, border: r.b, transform: `scale(${r.s})` }}
                        className="flex h-[88px] flex-col justify-end rounded-2xl p-3 transition-transform"
                      >
                        <b className="text-[14px] text-[#1C1B1F]">{r.n}</b>
                        <span className="text-[12px] text-[#1C1B1F]">{r.k}</span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Rede movel */}
                {f.slug === "movel" && (
                  <div className="text-center">
                    <div className="font-mono text-[44px] font-bold text-[#5B21D6]">4G / 5G</div>
                    <div className="mt-2 text-[15px] text-[#49454F]">Leitura do sinal da torre</div>
                  </div>
                )}
              </div>
            )}

            {/* Tool execution states */}
            {!isApp ? (
              <div>
                {phase === "idle" && (
                  <div className="text-center">
                    <button
                      type="button"
                      onClick={runTool}
                      className="cursor-pointer rounded-full border-0 bg-[#5B21D6] px-9 py-[18px] text-[18px] font-medium text-white shadow-[0_8px_20px_-8px_rgba(91,33,214,0.6)] transition-transform hover:-translate-y-0.5"
                    >
                      {btnLabel}
                    </button>
                    <div className="mt-[14px] text-[14px] text-[#49454F]">
                      Roda no seu navegador. Nenhum dado pessoal é coletado.
                    </div>
                  </div>
                )}

                {phase === "running" && (
                  <div className="text-center">
                    <div className="animate-pulse text-[16px] font-medium text-[#49454F]">{label}</div>
                    <div className="mt-3 flex items-baseline justify-center gap-2 font-mono text-[64px] font-bold leading-none tracking-[-3px]">
                      {unit === "ms" ? Math.round(live) : fmt(live)}
                      <span className="text-[20px] font-medium text-[#49454F]">{unit}</span>
                    </div>
                    <div className="mx-auto mt-7 h-[6px] max-w-[320px] overflow-hidden rounded-full bg-[#F3EEFA]">
                      <div style={{ width: `${pct}%` }} className="h-full rounded-full bg-[#5B21D6] transition-all" />
                    </div>
                    <button
                      type="button"
                      onClick={cancelTool}
                      className="mt-7 cursor-pointer rounded-full border-0 bg-[#F3EEFA] px-6 py-3 text-[15px] font-medium text-[#210A5C]"
                    >
                      Cancelar
                    </button>
                  </div>
                )}

                {phase === "done" && (
                  <div>
                    <div className="text-[12px] font-medium text-[#49454F]">Resultado</div>
                    <div className="mt-2 text-[26px] font-bold leading-8 tracking-[-0.5px]">{verdict}</div>
                    <p className="mt-2 text-[15px] leading-[22px] text-[#49454F]">{sub}</p>

                    {metrics.length > 0 && (
                      <div className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(100px,1fr))] gap-4 border-t border-[#E7E0EC] pt-4">
                        {metrics.map((m) => (
                          <div key={m.l}>
                            <div className="text-[12px] font-medium text-[#49454F]">{m.l}</div>
                            <div className="mt-1 text-[26px] font-bold tracking-[-1px]">
                              {m.v}
                              {m.u && <span className="text-[14px] font-medium text-[#49454F]"> {m.u}</span>}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}

                    <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-[#EAE0FF] p-4">
                      <span className="max-w-[15em] text-[15px] leading-[22px] text-[#210A5C]">
                        Quer saber a causa e o que fazer?
                      </span>
                      <Link
                        href="/#baixar"
                        className="rounded-full bg-[#5B21D6] px-5 py-2.5 text-[14px] font-medium text-white no-underline"
                      >
                        Baixar o app
                      </Link>
                    </div>

                    <button
                      type="button"
                      onClick={runTool}
                      className="mt-3 cursor-pointer border-0 bg-transparent py-1.5 text-[15px] font-semibold text-[#5B21D6]"
                    >
                      Testar de novo
                    </button>
                  </div>
                )}

                {phase === "error" && (
                  <div className="text-center">
                    <div className="text-[24px] font-bold">Não conseguimos medir.</div>
                    <p className="mx-auto mt-2 max-w-[22em] text-[15px] leading-[22px] text-[#49454F]">
                      Verifique se você está conectado e tente de novo.
                    </p>
                    <button
                      type="button"
                      onClick={runTool}
                      className="mt-5 cursor-pointer rounded-full border-0 bg-[#5B21D6] px-7 py-3.5 text-[16px] font-medium text-white"
                    >
                      Tentar de novo
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="mt-3 text-center">
                <div className="text-[12px] text-[#49454F]">Exemplo ilustrativo do que o app mostra</div>
                <Link
                  href="/#baixar"
                  className="mt-4 block rounded-full bg-[#5B21D6] px-7 py-3.5 text-[15px] font-medium text-white no-underline hover:bg-[#4416A8]"
                >
                  Ver no meu celular
                </Link>
              </div>
            )}
          </div>
        </section>
      </div>

      {/* Steps Section */}
      <section className="bg-[#F8F5FB] px-6 py-20">
        <div className="mx-auto max-w-[1120px]">
          <h2 className="m-0 max-w-[16em] text-balance text-[clamp(28px,3.4vw,40px)] font-bold leading-[1.1] tracking-[-1px]">
            {!isApp ? "Resultado em três passos." : "Em três passos, no seu celular."}
          </h2>
          <div className="mt-9 grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-4">
            {steps.map((s) => (
              <div key={s.n} className="rounded-3xl bg-white p-7">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#5B21D6] font-bold text-white">
                  {s.n}
                </div>
                <div className="mt-4 text-[22px] font-bold leading-7">{s.t}</div>
                <p className="mt-2 text-[16px] leading-6 text-[#49454F]">{s.d}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Rows breakdown */}
      <section className="px-6 pb-10 pt-24">
        <div className="mx-auto max-w-[1120px]">
          <h2 className="m-0 max-w-[16em] text-balance text-[clamp(28px,3.4vw,40px)] font-bold leading-[1.1] tracking-[-1px]">
            {f.rt}
          </h2>
          <div className="mt-9 grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
            {f.rows.map((r) => (
              <div
                key={r.a}
                className="rounded-[20px] bg-[#F8F5FB] p-6 transition-transform hover:-translate-y-1"
              >
                <div className="text-[22px] font-bold leading-7 text-[#5B21D6]">{r.a}</div>
                <p className="mt-2.5 text-[16px] leading-6 text-[#49454F]">{r.b}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Deep-dive sections */}
      <section className="px-6 py-12">
        {f.secs.map((s) => (
          <div
            key={s.h}
            className="mx-auto grid max-w-[1120px] grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-x-16 gap-y-6 border-t-2 border-[#1C1B1F] py-9 pb-14"
          >
            <h2 className="m-0 text-[28px] font-bold leading-[34px] tracking-[-0.5px]">{s.h}</h2>
            <div>
              {s.ps.map((p, idx) => (
                <p key={idx} className="m-0 mb-3.5 max-w-[36em] text-pretty text-[18px] leading-7 text-[#49454F]">
                  {p}
                </p>
              ))}
            </div>
          </div>
        ))}
      </section>

      {/* App pitch dark banner */}
      <section className="bg-black px-6 py-24 text-[#F5F2F7]">
        <div className="mx-auto grid max-w-[1120px] grid-cols-[repeat(auto-fit,minmax(min(280px,100%),1fr))] items-center gap-x-14 gap-y-10">
          <h2 className="m-0 max-w-[16em] text-balance text-[clamp(32px,4.2vw,52px)] font-bold leading-[1.05] tracking-[-1.2px]">
            {!isApp ? "Isso é só uma amostra. O app vai até a causa." : "Veja isso acontecendo na sua casa."}
          </h2>
          <div>
            <p className="m-0 text-[19px] leading-[29px] text-[#CAC4D0]">{f.app}</p>
            <Link
              href="/#baixar"
              className="mt-7 inline-block rounded-full bg-[#EAE0FF] px-8 py-[18px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white"
            >
              Baixar grátis no Google Play
            </Link>
            <div className="mt-3.5 text-[14px] text-[#CAC4D0]">Sem cadastro para começar</div>
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="mx-auto max-w-[768px] px-6 pb-16 pt-24">
        <h2 className="m-0 mb-8 text-balance text-[clamp(28px,3.4vw,40px)] font-bold leading-[1.1] tracking-[-1px]">
          Perguntas comuns
        </h2>
        {f.faq.map(([q, a]) => (
          <details key={q} className="group border-t border-[#E7E0EC] py-5">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[18px] font-semibold leading-6 [&::-webkit-details-marker]:hidden">
              <span>{q}</span>
              <span className="text-[26px] font-normal leading-none text-[#5B21D6] transition-transform duration-150 group-open:rotate-45">
                +
              </span>
            </summary>
            <p className="m-0 mt-3 max-w-[36em] text-pretty text-[16px] leading-6 text-[#49454F]">{a}</p>
          </details>
        ))}
      </section>

      {/* Related features */}
      <section className="mx-auto max-w-[1168px] px-6 pb-36 pt-6">
        <div className="mb-4 text-[14px] font-semibold text-[#49454F]">Veja também</div>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-4">
          {related.map((r) => (
            <Link
              key={r.slug}
              href={r.url}
              className="block rounded-3xl bg-[#F8F5FB] p-6 text-[#1C1B1F] no-underline transition-colors hover:bg-[#EAE0FF]"
            >
              <div className="text-[20px] font-semibold leading-[26px]">{r.nav}</div>
              <p className="mt-2 text-[15px] leading-[22px] text-[#49454F]">{r.short}</p>
              <span className="mt-3.5 inline-block text-[15px] font-semibold text-[#5B21D6]">Abrir →</span>
            </Link>
          ))}
        </div>
      </section>

      {/* Floating Bottom Bar on Scroll */}
      <div
        className={`fixed bottom-5 left-1/2 z-20 w-[calc(100%-32px)] max-w-[560px] -translate-x-1/2 transition-transform duration-500 ${
          barVisible ? "translate-y-0" : "translate-y-36"
        }`}
      >
        <div className="flex items-center justify-between gap-4 rounded-full bg-[#210A5C] py-2.5 pl-6 pr-2.5 text-white shadow-[0_16px_40px_-12px_rgba(33,10,92,0.6)]">
          <span className="text-[15px] font-medium leading-5">O app faz isso e muito mais.</span>
          <Link
            href="/#baixar"
            className="rounded-full bg-[#EAE0FF] px-5 py-3 text-[15px] font-semibold text-[#210A5C] no-underline hover:bg-white"
          >
            Baixar grátis
          </Link>
        </div>
      </div>
    </div>
  );
}
