"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { type FeatureData, getRelatedFeatures } from "@/lib/featuresData";
import { SIGNALLQ_PLAY_STORE_URL } from "@/lib/config";
import {
  FEATURE_DOWNLOAD_APP_CLICADO,
  FEATURE_TESTE_DNS_CONCLUIDO,
  FEATURE_TESTE_DNS_ERRO,
  FEATURE_TESTE_DNS_INICIADO,
  FEATURE_TESTE_JITTER_CONCLUIDO,
  FEATURE_TESTE_JITTER_ERRO,
  FEATURE_TESTE_JITTER_INICIADO,
  FEATURE_TESTE_PING_CONCLUIDO,
  FEATURE_TESTE_PING_ERRO,
  FEATURE_TESTE_PING_INICIADO,
  FEATURE_TESTE_VELOCIDADE_CONCLUIDO,
  FEATURE_TESTE_VELOCIDADE_ERRO,
  FEATURE_TESTE_VELOCIDADE_INICIADO,
  trackFeatureUsed,
} from "@/lib/telemetry";

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
  const [limitationNotice, setLimitationNotice] = useState("");
  const [barVisible, setBarVisible] = useState(false);
  const [tick, setTick] = useState(0);

  const runRef = useRef(0);

  // Animação de tick respeitando prefers-reduced-motion e visibilitychange
  useEffect(() => {
    if (typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }

    let timer: ReturnType<typeof setInterval> | null = null;
    const startTimer = () => {
      if (!timer) timer = setInterval(() => setTick((t) => t + 1), 120);
    };
    const stopTimer = () => {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    };
    const handleVisibility = () => {
      if (typeof document !== "undefined" && document.hidden) stopTimer();
      else startTimer();
    };

    if (typeof document !== "undefined") {
      document.addEventListener("visibilitychange", handleVisibility);
      if (!document.hidden) startTimer();
    }

    const onScroll = () => {
      const y = window.scrollY;
      const h = document.documentElement.scrollHeight - window.innerHeight;
      setBarVisible(y > 500 && y < h - 400);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      stopTimer();
      if (typeof document !== "undefined") {
        document.removeEventListener("visibilitychange", handleVisibility);
      }
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

    // Dispara telemetria de início
    if (f.tool === "ping") trackFeatureUsed(FEATURE_TESTE_PING_INICIADO);
    else if (f.tool === "jitter") trackFeatureUsed(FEATURE_TESTE_JITTER_INICIADO);
    else if (f.tool === "speed") trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_INICIADO);
    else if (f.tool === "dns") trackFeatureUsed(FEATURE_TESTE_DNS_INICIADO);

    setPhase("running");
    setLive(0);
    setPct(3);
    setUnit("ms");
    setMetrics([]);
    setLabel("Medindo…");
    setLimitationNotice("");

    try {
      if (f.tool === "ping" || f.tool === "jitter") {
        const n = f.tool === "ping" ? 10 : 20;
        const s: number[] = [];
        let lost = 0;
        setLabel(f.tool === "ping" ? "Medindo latência HTTP…" : "Medindo estabilidade…");

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
              ? ["Seu tempo de resposta HTTP está ótimo.", "Resposta ágil até a borda regional. Sem atraso perceptível."]
              : avg < 60
              ? ["Seu tempo de resposta HTTP está bom.", "Atende a maioria das atividades cotidianas."]
              : avg < 100
              ? ["Seu tempo de resposta HTTP está moderado.", "Jogos competitivos e chamadas podem sentir pequenos atrasos."]
              : ["Tempo de resposta HTTP alto.", "Atraso perceptível. Pode indicar rota congestionada ou Wi-Fi distante."];
          setVerdict(v[0]);
          setSub(v[1]);
          setMetrics([
            { l: "Média", v: Math.round(avg), u: "ms" },
            { l: "Menor", v: Math.round(min), u: "ms" },
            { l: "Maior", v: Math.round(max), u: "ms" },
          ]);
          setLimitationNotice(
            "Limitação da medição web: este teste mede a latência de requisições HTTP até a borda CDN regional. Não equivale a um comando ping ICMP de baixo nível. Para isolar se o atraso se origina no canal Wi-Fi do roteador ou na operadora, utilize o SignallQ para Android."
          );
          trackFeatureUsed(FEATURE_TESTE_PING_CONCLUIDO);
        } else {
          let j = 0;
          for (let i = 1; i < s.length; i++) j += Math.abs(s[i] - s[i - 1]);
          j = s.length > 1 ? j / (s.length - 1) : 0;
          const timeoutRate = Math.round((lost / n) * 100);
          const v =
            j < 10 && timeoutRate === 0
              ? ["Conexão estável.", "Pouca variação no tempo de resposta e sem timeouts. Bom para chamadas e jogos."]
              : j < 30 && timeoutRate <= 5
              ? ["Estabilidade aceitável.", "Há alguma oscilação no tempo de resposta. Reuniões longas podem ter pequenas travadas."]
              : ["Conexão com oscilação relevante.", "Variação perceptível no tempo de resposta ou timeouts detectados."];
          setVerdict(v[0]);
          setSub(v[1]);
          setMetrics([
            { l: "Latência média", v: Math.round(avg), u: "ms" },
            { l: "Jitter", v: fmt(j), u: "ms" },
            { l: "Timeouts HTTP", v: timeoutRate, u: "%" },
          ]);
          setLimitationNotice(
            "Limitação da medição web: falhas de requisição HTTP indicam timeouts ou instabilidade temporária na camada web, mas não equivalem a perda física de pacotes UDP/ICMP. A perda de pacotes e o diagnóstico de canais do roteador são medidos pelo app Android."
          );
          trackFeatureUsed(FEATURE_TESTE_JITTER_CONCLUIDO);
        }
        setPhase("done");
      } else if (f.tool === "speed") {
        setLabel("Medindo latência…");
        const t: number[] = [];
        for (let i = 0; i < 8; i++) {
          const v = await ping1(2000);
          if (!ok()) return;
          if (v != null) t.push(v);
          await new Promise((r) => setTimeout(r, 100));
        }
        if (!t.length) throw new Error("Latency failed");

        setLabel("Medindo download…");
        setUnit("Mbps");
        setLive(0);
        setPct(15);

        const dlDuration = 8;
        const t0 = performance.now();
        let bytes = 0;
        let warmUpBytes = 0;
        let warmUpTime = 0;
        let warmedUp = false;
        let dl = 0;
        const szs = [5e6, 10e6, 25e6, 25e6];
        let szIdx = 0;

        while ((performance.now() - t0) / 1000 < dlDuration && ok()) {
          const sz = szs[Math.min(szIdx++, szs.length - 1)];
          const r = await fetch(`${BASE}/__down?bytes=${sz}&r=${Math.random()}`, { cache: "no-store" });
          const rd = r.body?.getReader();
          if (!rd) break;
          let streamDone = false;
          while (!streamDone && ok()) {
            const { done, value } = await rd.read();
            if (done) {
              streamDone = true;
              break;
            }
            bytes += value.length;
            const el = (performance.now() - t0) / 1000;
            if (!warmedUp && el >= 1.2) {
              warmedUp = true;
              warmUpBytes = bytes;
              warmUpTime = el;
            }
            if (warmedUp && el > warmUpTime) {
              dl = ((bytes - warmUpBytes) * 8) / (el - warmUpTime) / 1e6;
            } else {
              dl = (bytes * 8) / Math.max(0.1, el) / 1e6;
            }
            if (!ok()) {
              rd.cancel();
              return;
            }
            setLive(dl);
            setPct(15 + Math.min(45, (el / dlDuration) * 45));
            if (el >= dlDuration) {
              rd.cancel();
              break;
            }
          }
        }

        setLabel("Medindo upload…");
        setLive(0);
        setPct(60);
        const blob = new Uint8Array(2e6);
        for (let b = 0; b < blob.length; b += 1024) blob[b] = (b + Math.floor(Math.random() * 255)) & 0xff;
        const u0 = performance.now();
        const upDuration = 6;
        let ub = 0;
        let upWarmBytes = 0;
        let upWarmTime = 0;
        let upWarmed = false;
        let ul = 0;

        while ((performance.now() - u0) / 1000 < upDuration && ok()) {
          await fetch(`${BASE}/__up`, { method: "POST", body: blob, cache: "no-store" });
          ub += blob.length;
          const uel = (performance.now() - u0) / 1000;
          if (!upWarmed && uel >= 1.0) {
            upWarmed = true;
            upWarmBytes = ub;
            upWarmTime = uel;
          }
          if (upWarmed && uel > upWarmTime) {
            ul = ((ub - upWarmBytes) * 8) / (uel - upWarmTime) / 1e6;
          } else {
            ul = (ub * 8) / Math.max(0.1, uel) / 1e6;
          }
          if (!ok()) return;
          setLive(ul);
          setPct(60 + Math.min(38, (uel / upDuration) * 38));
        }

        const v =
          dl >= 100
            ? ["Sua velocidade está ótima.", "Capacidade suficiente para múltiplos aparelhos simultâneos e streaming 4K."]
            : dl >= 25
            ? ["Sua velocidade está boa.", "Atende bem o uso diário: vídeos em HD, chamadas e redes sociais."]
            : dl >= 8
            ? ["Velocidade básica.", "Adequada para mensagens e páginas simples. Downloads pesados demoram."]
            : ["Velocidade reduzida.", "Pode causar travamentos frequentes em vídeos e chamadas."];

        setVerdict(v[0]);
        setSub(v[1]);
        setPct(100);
        setMetrics([
          { l: "Download", v: fmt(dl), u: "Mbps" },
          { l: "Upload", v: fmt(ul), u: "Mbps" },
          { l: "Latência", v: Math.round(Math.min(...t)), u: "ms" },
        ]);
        setLimitationNotice(
          "Limitação da medição web: velocidade adequada não elimina travamentos causados por interferência de Wi-Fi, sinal fraco ou bufferbloat no roteador. O app Android analisa todas as variáveis em conjunto."
        );
        trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_CONCLUIDO);
        setPhase("done");
      } else if (f.tool === "dns") {
        const rs = [
          ["Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query?name=google.com&type=A"],
          ["Google (8.8.8.8)", "https://dns.google/resolve?name=google.com&type=A"],
          ["Quad9 (9.9.9.9)", "https://dns.quad9.net:5053/dns-query?name=google.com&type=A"],
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

        setVerdict(`${good[0].l.split(" ")[0]} respondeu mais rápido via DoH.`);
        setSub(
          (good[0].n as number) < 50
            ? "Tempo de resolução ágil. Trocar o DNS traria pouca diferença no carregamento inicial."
            : "Se as páginas demoram a começar a carregar, configurar um DNS mais ágil pode melhorar a resposta."
        );
        setMetrics(out.map((o) => ({ l: o.l, v: o.n == null ? "—" : Math.round(o.n), u: o.n == null ? "" : "ms" })));
        setLimitationNotice(
          "Limitação da medição web: este teste compara o tempo de resolução via DNS-over-HTTPS (DoH) dos resolvedores públicos a partir do navegador. Não mede diretamente o servidor DNS local configurado no seu roteador (o app Android analisa a configuração DNS do aparelho e da rede)."
        );
        trackFeatureUsed(FEATURE_TESTE_DNS_CONCLUIDO);
        setPhase("done");
      } else if (f.tool === "mobile") {
        const nav = typeof navigator !== "undefined" ? (navigator as unknown as { connection?: { effectiveType?: string; downlink?: number; rtt?: number; saveData?: boolean } }) : {};
        const c = nav.connection;
        if (!c) {
          setVerdict("Seu navegador não informa a rede.");
          setSub("Esta informação depende do suporte da Network Information API do navegador. Para ler o sinal real em dBm da torre celular, use o app Android.");
          setMetrics([]);
        } else {
          const tp: Record<string, string> = { "slow-2g": "2G lento", "2g": "2G", "3g": "3G", "4g": "4G ou melhor" };
          setVerdict(`Tipo de conexão estimado: ${tp[c.effectiveType || ""] || c.effectiveType || "desconhecido"}.`);
          setSub("Estimativa aproximada gerada pelo navegador. Não substitui a leitura de potência e banda pelo modem do celular.");
          setMetrics([
            { l: "Velocidade estimada", v: c.downlink != null ? c.downlink : "—", u: "Mbps" },
            { l: "Tempo de resposta", v: c.rtt != null ? c.rtt : "—", u: "ms" },
            { l: "Economia de dados", v: c.saveData ? "Ligada" : "Desligada", u: "" },
          ]);
        }
        setLimitationNotice(
          "Limitação da medição web: o navegador não tem acesso às informações de rádio do chip SIM ou das antenas. A potência do sinal (dBm) e a tecnologia real da operadora são lidas exclusivamente no app Android."
        );
        setPhase("done");
      }
    } catch {
      if (ok()) {
        if (f.tool === "ping") trackFeatureUsed(FEATURE_TESTE_PING_ERRO);
        else if (f.tool === "jitter") trackFeatureUsed(FEATURE_TESTE_JITTER_ERRO);
        else if (f.tool === "speed") trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_ERRO);
        else if (f.tool === "dns") trackFeatureUsed(FEATURE_TESTE_DNS_ERRO);
        setPhase("error");
      }
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

  // Visualizer calculations based on tick (used by app-exclusive illustrations)
  const sn = Math.sin;
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
                <a
                  href={SIGNALLQ_PLAY_STORE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={() => trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO)}
                  className="rounded-full bg-[#EAE0FF] px-[30px] py-[17px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors"
                >
                  Baixar no Google Play
                </a>
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
              <div className="mb-6">
                {/* Identificação explícita para ferramentas exclusivas do app */}
                {isApp && (
                  <div className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-[#F3EEFA] px-3.5 py-1 text-[12px] font-semibold text-[#5B21D6]">
                    <span aria-hidden="true">📱</span> Demonstração ilustrativa (recurso do app)
                  </div>
                )}

                {/* Placeholders neutros no estado idle para ferramentas web */}
                {!isApp && (f.slug === "ping" || f.slug === "jitter") && (
                  <div className="text-center">
                    <div className="text-[12px] font-semibold uppercase tracking-wider text-[#79747E]">
                      Aguardando medição
                    </div>
                    <div className="mt-2 flex items-baseline justify-center gap-2">
                      <span className="font-mono text-[64px] font-bold leading-none text-[#CAC4D0] tracking-[-2px]">—</span>
                      <span className="text-[18px] font-medium text-[#79747E]">ms</span>
                    </div>
                    <p className="mt-2.5 text-[14px] text-[#49454F]">
                      {f.slug === "ping"
                        ? "Mede o tempo real de resposta da sua conexão via HTTP."
                        : "Calcula a oscilação real (jitter) através de medições consecutivas."}
                    </p>
                  </div>
                )}

                {!isApp && f.slug === "dns" && (
                  <div className="flex flex-col gap-2.5">
                    <div className="text-[12px] font-semibold uppercase tracking-wider text-[#79747E] text-center mb-1">
                      Resolvedores para comparação DoH
                    </div>
                    {["Cloudflare (1.1.1.1)", "Google (8.8.8.8)", "Quad9 (9.9.9.9)"].map((n) => (
                      <div key={n} className="flex justify-between items-center rounded-xl bg-[#F8F5FB] px-4 py-2.5 text-[14px] font-medium text-[#49454F]">
                        <span>{n}</span>
                        <span className="font-mono font-bold text-[#79747E]">— ms</span>
                      </div>
                    ))}
                  </div>
                )}

                {!isApp && f.slug === "velocidade" && (
                  <div className="flex flex-col items-center text-center">
                    <div className="text-[12px] font-semibold uppercase tracking-wider text-[#79747E]">Aguardando início</div>
                    <div className="mt-2 font-mono text-[64px] font-bold leading-none text-[#CAC4D0]">—</div>
                    <div className="mt-1 text-[15px] font-medium text-[#79747E]">Mbps · Download e Upload</div>
                  </div>
                )}

                {/* Demonstrações das ferramentas do App */}
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
                    <div className="text-[12px] font-medium text-[#49454F]">Resultado da medição</div>
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

                    {limitationNotice && (
                      <div className="mt-4 rounded-xl bg-[#F8F9FA] border border-[#E7E0EC] p-3 text-left text-[12px] leading-5 text-[#79747E]">
                        <strong className="text-[#49454F]">Nota técnica:</strong> {limitationNotice}
                      </div>
                    )}

                    <div className="mt-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-2xl bg-[#EAE0FF] p-4 text-left">
                      <div>
                        <div className="text-[15px] font-bold text-[#210A5C]">
                          Quer descobrir a causa e o que fazer?
                        </div>
                        <div className="text-[13px] text-[#49454F] mt-0.5">
                          O app Android analisa canais Wi-Fi, DNS local e perdas de pacotes.
                        </div>
                      </div>
                      <a
                        href={SIGNALLQ_PLAY_STORE_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={() => trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO)}
                        className="rounded-full bg-[#5B21D6] px-5 py-2.5 text-[14px] font-medium text-white no-underline hover:bg-[#4A18B0] text-center transition-colors shrink-0"
                      >
                        Baixar o app
                      </a>
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
                <a
                  href={SIGNALLQ_PLAY_STORE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={() => trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO)}
                  className="mt-4 block rounded-full bg-[#5B21D6] px-7 py-3.5 text-[15px] font-medium text-white no-underline hover:bg-[#4416A8] transition-colors"
                >
                  Ver no meu celular
                </a>
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
            <a
              href={SIGNALLQ_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO)}
              className="mt-7 inline-block rounded-full bg-[#EAE0FF] px-8 py-[18px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors"
            >
              Baixar grátis no Google Play
            </a>
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
