"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";

const GAMES_DATA: [string, string, string][] = [
  ['fortnite', 'Fortnite', 'Battle royale'],
  ['freefire', 'Free Fire', 'Battle royale'],
  ['codm', 'COD Mobile', 'FPS'],
  ['valorant', 'Valorant', 'FPS'],
  ['cs2', 'Counter-Strike 2', 'FPS'],
  ['pubgm', 'PUBG Mobile', 'Battle royale'],
  ['apex', 'Apex Legends', 'Battle royale'],
  ['lol', 'League of Legends', 'MOBA'],
  ['mlbb', 'Mobile Legends', 'MOBA'],
  ['rocket', 'Rocket League', 'Esportes'],
  ['fc', 'EA Sports FC', 'Esportes'],
  ['brawl', 'Brawl Stars', 'Casual'],
  ['roblox', 'Roblox', 'Casual'],
  ['minecraft', 'Minecraft', 'Casual'],
];

const DEVICES = ['PlayStation', 'Xbox', 'PC', 'Android', 'iPhone', 'Switch', 'TV / cloud'];
const GENRES = ['Todos', 'Battle royale', 'FPS', 'MOBA', 'Esportes', 'Casual'];

interface Metric {
  l: string;
  v: string | number;
  u: string;
}

const FAQ = [
  {
    q: 'O app mostra o ping do meu servidor de jogo?',
    a: 'Não. Ele mede uma rota regional de referência e avalia a conexão para o tipo de jogo. Não mede o servidor do jogo, por isso nunca promete “o ping do seu jogo”.',
  },
  {
    q: 'Meu jogo não está na lista. E agora?',
    a: 'Escolha “Meu jogo não está na lista” e uma das 6 categorias genéricas. O veredito continua válido.',
  },
  {
    q: 'Preciso de Android para usar?',
    a: 'O app é para Android, mas você pode avaliar a conexão para jogar em PlayStation, Xbox, PC, Switch, iPhone ou TV.',
  },
];

export default function ModoGamerContent() {
  const [selectedGame, setSelectedGame] = useState('Fortnite');
  const [selectedGenre, setSelectedGenre] = useState('Todos');
  const [tick, setTick] = useState(0);

  // Test state
  const [phase, setPhase] = useState<'idle' | 'running' | 'done' | 'error'>('idle');
  const [live, setLive] = useState(0);
  const [pct, setPct] = useState(0);
  const [metrics, setMetrics] = useState<Metric[]>([]);
  const [verdict, setVerdict] = useState('');
  const [sub, setSub] = useState('');

  const runRef = useRef(0);

  useEffect(() => {
    const timer = setInterval(() => setTick((t) => t + 1), 70);
    return () => clearInterval(timer);
  }, []);

  // Demo card calculations based on tick
  const sn = Math.sin;
  const D = [
    { t: 'Pronto para ranked', c: '#7DDB92', s: 'Rota estável. Pode entrar na fila.', lat: 24, jit: 3, loss: 0, amp: 0.3, g: 'Fortnite' },
    { t: 'Pode ter atrasos', c: '#FFB867', s: 'Jitter no limite. Feche downloads e tente de novo.', lat: 58, jit: 19, loss: 1, amp: 0.7, g: 'Free Fire' },
    { t: 'Evite o ranked agora', c: '#FF8A7A', s: 'Perda alta na rota. Tente cabo ou chegue perto do roteador.', lat: 112, jit: 46, loss: 4, amp: 1, g: 'COD Mobile' },
  ];
  const demoK = Math.floor(tick / 40) % 3;
  const dv = D[demoK];
  const demoBars = Array.from({ length: 28 }, (_, i) => ({
    h: Math.round(22 + 50 * (0.5 + 0.5 * sn(i * 0.5 + tick * 0.2)) * (1 - dv.amp * 0.5 + dv.amp * 0.5 * Math.abs(sn(i * 7.3 + tick * 0.4))) + dv.amp * 14),
  }));

  const filteredGames = GAMES_DATA.filter((g) => selectedGenre === 'Todos' || g[2] === selectedGenre);

  const ping1 = async (ms = 1500): Promise<number | null> => {
    const c = new AbortController();
    const t = setTimeout(() => c.abort(), ms);
    const s = performance.now();
    try {
      await fetch(`https://speed.cloudflare.com/__down?bytes=0&r=${Math.random()}`, {
        cache: 'no-store',
        signal: c.signal,
      });
      clearTimeout(t);
      return performance.now() - s;
    } catch {
      clearTimeout(t);
      return null;
    }
  };

  const runTest = async () => {
    const id = ++runRef.current;
    const ok = () => runRef.current === id;

    setPhase('running');
    setLive(0);
    setPct(3);
    setMetrics([]);

    try {
      const n = 20;
      const s: number[] = [];
      let lost = 0;

      for (let i = 0; i < n; i++) {
        const v = await ping1(1500);
        if (!ok()) return;
        if (v == null) lost++;
        else s.push(v);
        setLive(s.length ? Math.round(s[s.length - 1]) : 0);
        setPct(Math.round(((i + 1) / n) * 100));
      }

      if (!s.length) throw new Error('Failed');

      const avg = s.reduce((a, b) => a + b, 0) / s.length;
      let j = 0;
      for (let i = 1; i < s.length; i++) j += Math.abs(s[i] - s[i - 1]);
      j = s.length > 1 ? j / (s.length - 1) : 0;
      const loss = Math.round((lost / n) * 100);

      const v =
        j < 10 && loss === 0
          ? ['Conexão estável.', 'Pouca oscilação e nenhuma perda. Bom sinal para jogar online.']
          : j < 30 && loss <= 2
          ? ['Estabilidade aceitável.', 'Há alguma oscilação. Jogos competitivos podem sentir.']
          : ['Conexão instável.', 'Oscilação ou perda altas. Espere travadas e saltos na partida.'];

      setVerdict(v[0]);
      setSub(v[1]);
      setMetrics([
        { l: 'Latência média', v: Math.round(avg), u: 'ms' },
        { l: 'Jitter', v: j >= 100 ? Math.round(j) : j.toFixed(1), u: 'ms' },
        { l: 'Perda de pacotes', v: loss, u: '%' },
      ]);
      setPhase('done');
    } catch {
      if (ok()) setPhase('error');
    }
  };

  const cancelTest = () => {
    runRef.current++;
    setPhase('idle');
  };

  return (
    <div className="bg-[#0B0D10] font-sans leading-[normal] text-[#F5F2F7]">
      {/* Hero */}
      <section className="mx-auto grid max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(min(340px,100%),1fr))] items-center gap-14 px-6 pb-20 pt-[72px]">
        <div>
          <Link
            href="/funcionalidades"
            className="inline-block text-[14px] font-medium text-[#CAC4D0] no-underline hover:text-white"
          >
            ← Funcionalidades
          </Link>

          <div className="mt-7 flex h-[30px] w-fit items-center gap-2 rounded-full border border-[#3A3446] px-3.5 text-[13px] font-semibold text-[#C9B6FF]">
            <span className="h-2 w-2 rounded-full bg-[#7DDB92]" />
            Modo Jogos · no app SignallQ
          </div>

          <h1 className="m-0 mt-5 text-balance text-[clamp(42px,6vw,72px)] font-bold leading-none tracking-[-2px]">
            Sua internet está pronta para o ranked?
          </h1>

          <p className="mt-6 max-w-[28em] text-pretty text-[20px] leading-[30px] text-[#CAC4D0]">
            Velocidade não diz se a partida vai travar. O Modo Jogos mede ping, jitter e perda e responde se dá para jogar aquele jogo, naquele aparelho, agora.
          </p>

          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              href="/#baixar"
              className="rounded-full bg-[#EAE0FF] px-[30px] py-[17px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white"
            >
              Baixar no Google Play
            </Link>
            <a
              href="#teste"
              className="rounded-full border border-[#4A4458] px-7 py-4 text-[17px] font-medium text-[#F5F2F7] no-underline hover:border-[#C9B6FF]"
            >
              Testar aqui, sem baixar
            </a>
          </div>

          <div className="mt-5 text-[14px] text-[#A9A3B5]">
            Grátis · Sem cadastro · Para PlayStation, Xbox, PC, Switch, iPhone e TV
          </div>
        </div>

        {/* Demo card */}
        <div className="flex justify-center">
          <div className="w-full max-w-[380px] rounded-[32px] border border-[#262B33] bg-[#14171C] p-7 shadow-[0_40px_80px_-30px_rgba(91,33,214,0.55)]">
            <div className="flex items-center justify-between text-[14px] text-[#A9A3B5]">
              <span>Check de conexão</span>
              <span className="rounded-full bg-[#1F242C] px-3 py-1 font-semibold text-[#F5F2F7]">
                {selectedGame || dv.g}
              </span>
            </div>

            <div style={{ color: dv.c }} className="mt-7 text-[30px] font-bold leading-[34px] tracking-[-0.8px]">
              {dv.t}
            </div>
            <div className="mt-2 text-[15px] leading-[22px] text-[#A9A3B5]">{dv.s}</div>

            <div className="mt-6 flex h-[84px] items-end gap-1">
              {demoBars.map((b, i) => (
                <div key={i} style={{ height: `${b.h}%`, background: dv.c }} className="flex-1 rounded-[3px] opacity-85" />
              ))}
            </div>

            <div className="mt-6 grid grid-cols-3 gap-3 border-t border-[#262B33] pt-5">
              <div>
                <div className="text-[12px] text-[#A9A3B5]">Latência</div>
                <div className="mt-1 font-mono text-[26px] font-bold">
                  {dv.lat}
                  <span className="text-[13px] font-medium text-[#A9A3B5]"> ms</span>
                </div>
              </div>
              <div>
                <div className="text-[12px] text-[#A9A3B5]">Jitter</div>
                <div className="mt-1 font-mono text-[26px] font-bold">
                  {dv.jit}
                  <span className="text-[13px] font-medium text-[#A9A3B5]"> ms</span>
                </div>
              </div>
              <div>
                <div className="text-[12px] text-[#A9A3B5]">Perda</div>
                <div className="mt-1 font-mono text-[26px] font-bold">
                  {dv.loss}
                  <span className="text-[13px] font-medium text-[#A9A3B5]"> %</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Catalog */}
      <section id="jogos" className="border-t border-[#1F242C] px-6 pb-6 pt-16">
        <div className="mx-auto max-w-[1120px]">
          <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
            <div>
              <div className="text-[14px] font-semibold text-[#C9B6FF]">Catálogo</div>
              <h2 className="m-0 mt-2.5 text-[clamp(28px,3.6vw,44px)] font-bold leading-[1.08] tracking-[-1px]">
                Comece pelo jogo que você vai abrir.
              </h2>
            </div>
            <div className="text-[15px] text-[#A9A3B5]">21 títulos e 6 categorias genéricas no app</div>
          </div>

          <div className="mt-7 flex flex-wrap gap-2">
            {GENRES.map((g) => (
              <button
                key={g}
                type="button"
                onClick={() => setSelectedGenre(g)}
                className={`h-10 cursor-pointer rounded-full border px-4 text-[15px] font-medium transition-colors ${
                  selectedGenre === g
                    ? 'border-[#EAE0FF] bg-[#EAE0FF] text-[#210A5C]'
                    : 'border-[#3A3446] bg-transparent text-[#E7E0EC] hover:border-[#7B4DF0]'
                }`}
              >
                {g}
              </button>
            ))}
          </div>

          <div className="mt-7 grid grid-cols-[repeat(auto-fill,minmax(min(168px,100%),1fr))] gap-3">
            {filteredGames.map(([id, n, k]) => {
              const sel = selectedGame === n;
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => setSelectedGame(n)}
                  className={`flex cursor-pointer flex-col gap-3 rounded-3xl border p-4 text-left transition-colors hover:border-[#7B4DF0] ${
                    sel ? 'border-[#7B4DF0] bg-[#1D1535]' : 'border-[#262B33] bg-[#14171C]'
                  }`}
                >
                  <div className="flex aspect-square w-full items-center justify-center rounded-[22%] bg-[#1F242C] text-[13px] font-bold text-[#A9A3B5]">
                    {n}
                  </div>
                  <div>
                    <div className="text-[16px] font-semibold leading-5 text-[#F5F2F7]">{n}</div>
                    <div className="mt-1 text-[13px] text-[#A9A3B5]">{k}</div>
                  </div>
                  <div className={`flex items-center gap-1.5 text-[12px] font-semibold ${sel ? 'text-[#C9B6FF]' : 'text-[#7DDB92]'}`}>
                    <span className={`h-[7px] w-[7px] rounded-full ${sel ? 'bg-[#C9B6FF]' : 'bg-[#7DDB92]'}`} />
                    {sel ? 'Selecionado' : 'Check disponível'}
                  </div>
                </button>
              );
            })}
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-2">
            <span className="mr-2 text-[14px] text-[#A9A3B5]">Aparelhos</span>
            {DEVICES.map((d) => (
              <span key={d} className="inline-flex h-[34px] items-center rounded-xl border border-[#262B33] bg-[#14171C] px-3.5 text-[14px] text-[#E7E0EC]">
                {d}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* AWS info card */}
      <section className="px-6 pb-16 pt-10">
        <div className="mx-auto flex max-w-[1120px] flex-wrap items-center gap-x-8 gap-y-5 rounded-[28px] border border-[#262B33] bg-[#14171C] p-8">
          <div className="flex h-16 w-28 flex-none items-center justify-center rounded-xl bg-white font-bold text-black">
            AWS
          </div>
          <div className="min-w-[240px] flex-1">
            <div className="text-[20px] font-bold">Rota de referência em servidores da AWS</div>
            <p className="m-0 mt-1.5 max-w-[44em] text-[15px] leading-[22px] text-[#A9A3B5]">
              O check mede a rota até um servidor regional de referência. É uma amostra confiável da sua conexão, não o servidor do seu jogo.
            </p>
          </div>
        </div>
      </section>

      {/* Steps (Light contrast section) */}
      <section className="bg-[#F8F5FB] px-6 py-24 text-[#1C1B1F]">
        <div className="mx-auto max-w-[1120px]">
          <div className="text-[14px] font-semibold text-[#5B21D6]">Sem adivinhação</div>
          <h2 className="m-0 mt-3 max-w-[16em] text-balance text-[clamp(30px,4vw,48px)] font-bold leading-[1.05] tracking-[-1.2px]">
            Antes da fila, um check que respeita o que acontece na partida.
          </h2>
          <div className="mt-11 grid grid-cols-[repeat(auto-fit,minmax(min(260px,100%),1fr))] gap-4">
            <div className="rounded-[28px] bg-white p-8">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#5B21D6] font-bold text-white">1</div>
              <div className="mt-5 text-[24px] font-bold leading-[30px]">Escolha jogo e aparelho</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#49454F]">
                Catálogo de 21 títulos ou 6 categorias genéricas. PlayStation, Xbox, PC, Android, iPhone, Switch ou TV.
              </p>
            </div>
            <div className="rounded-[28px] bg-white p-8">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#5B21D6] font-bold text-white">2</div>
              <div className="mt-5 text-[24px] font-bold leading-[30px]">Faça o check</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#49454F]">
                O app envia uma sonda UDP real a uma rota regional de referência. Se bloqueado, cai para HTTPS.
              </p>
            </div>
            <div className="rounded-[28px] bg-white p-8">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#5B21D6] font-bold text-white">3</div>
              <div className="mt-5 text-[24px] font-bold leading-[30px]">Receba o veredito</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#49454F]">
                Em palavras, com a causa provável e o que fazer, em vez de uma tabela de números.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Verdicts */}
      <section className="px-6 py-24">
        <div className="mx-auto max-w-[1120px]">
          <div className="text-[14px] font-semibold text-[#C9B6FF]">Quatro respostas possíveis</div>
          <h2 className="m-0 mt-3 max-w-[16em] text-balance text-[clamp(30px,4vw,48px)] font-bold leading-[1.05] tracking-[-1.2px]">
            Um veredito honesto, nunca um chute.
          </h2>
          <div className="mt-11 grid grid-cols-[repeat(auto-fit,minmax(min(240px,100%),1fr))] gap-4">
            <div className="rounded-3xl border border-[#262B33] bg-[#14171C] p-7">
              <div className="h-3 w-3 rounded-full bg-[#7DDB92]" />
              <div className="mt-4 text-[22px] font-bold text-[#7DDB92]">Bom pra jogar</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#CAC4D0]">Tempo de resposta, variação e falhas dentro do esperado.</p>
            </div>
            <div className="rounded-3xl border border-[#262B33] bg-[#14171C] p-7">
              <div className="h-3 w-3 rounded-full bg-[#FFB867]" />
              <div className="mt-4 text-[22px] font-bold text-[#FFB867]">Pode ter atrasos</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#CAC4D0]">Algum indicador está no limite. Vale seguir as ações sugeridas.</p>
            </div>
            <div className="rounded-3xl border border-[#262B33] bg-[#14171C] p-7">
              <div className="h-3 w-3 rounded-full bg-[#FF8A7A]" />
              <div className="mt-4 text-[22px] font-bold text-[#FF8A7A]">Não recomendado</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#CAC4D0]">Atraso, oscilação ou falhas altas para esse tipo de jogo.</p>
            </div>
            <div className="rounded-3xl border border-[#262B33] bg-[#14171C] p-7">
              <div className="h-3 w-3 rounded-full bg-[#A9A3B5]" />
              <div className="mt-4 text-[22px] font-bold text-[#E7E0EC]">Sem dados suficientes</div>
              <p className="mt-2.5 text-[16px] leading-6 text-[#CAC4D0]">O app prefere dizer isso a chutar um veredito.</p>
            </div>
          </div>
        </div>
      </section>

      {/* Comparison */}
      <section className="px-6 pb-24 pt-6">
        <div className="mx-auto max-w-[1120px] rounded-[36px] border border-[#262B33] bg-[#14171C] p-8 sm:p-14">
          <div className="text-[14px] font-semibold text-[#C9B6FF]">Não é só velocidade</div>
          <h2 className="m-0 mt-3 max-w-[18em] text-balance text-[clamp(28px,3.6vw,44px)] font-bold leading-[1.08] tracking-[-1px]">
            Download alto não responde se a partida vai ficar estável.
          </h2>
          <div className="mt-10 grid grid-cols-[repeat(auto-fit,minmax(min(280px,100%),1fr))] gap-4">
            <div className="rounded-3xl border border-[#262B33] p-7">
              <div className="text-[20px] font-bold text-[#A9A3B5]">Teste de velocidade</div>
              <p className="mt-2 text-[15px] leading-[22px] text-[#A9A3B5]">Mede quanto chega, em um momento.</p>
              <div className="mt-5 flex flex-col gap-2.5 text-[16px] text-[#A9A3B5]">
                <span>Download</span>
                <span>Upload</span>
                <span>Ping pontual</span>
              </div>
            </div>
            <div className="rounded-3xl border border-[#5B21D6] bg-[#1D1535] p-7">
              <div className="text-[20px] font-bold text-white">Modo Jogos</div>
              <p className="mt-2 text-[15px] leading-[22px] text-[#E7DEFF]">Mede o que pesa para jogar naquela rota.</p>
              <div className="mt-5 flex flex-col gap-2.5 text-[16px] font-medium text-white">
                <span>Tempo de resposta</span>
                <span>Jitter (variação)</span>
                <span>Perda de pacotes</span>
                <span>Veredito para o seu jogo</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Interactive web test */}
      <section id="teste" className="px-6 pb-24">
        <div className="mx-auto max-w-[760px] text-center">
          <div className="text-[14px] font-semibold text-[#C9B6FF]">Teste rápido no navegador</div>
          <h2 className="m-0 mt-3 text-balance text-[clamp(28px,3.6vw,44px)] font-bold leading-[1.08] tracking-[-1px]">
            Veja a estabilidade da sua conexão agora.
          </h2>
          <p className="mx-auto mt-4 max-w-[30em] text-[18px] leading-7 text-[#CAC4D0]">
            20 medições em cerca de 30 segundos. É uma amostra: o veredito por jogo e aparelho fica no app.
          </p>

          <div className="mt-9 min-h-[260px] rounded-[32px] bg-white p-7 text-left text-[#1C1B1F] sm:p-9">
            {phase === 'idle' && (
              <div className="text-center">
                <button
                  type="button"
                  onClick={runTest}
                  className="cursor-pointer rounded-full border-0 bg-[#5B21D6] px-10 py-[18px] text-[18px] font-medium text-white shadow-[0_8px_20px_-8px_rgba(91,33,214,0.6)] hover:-translate-y-0.5"
                >
                  Medir estabilidade
                </button>
                <div className="mt-3.5 text-[14px] text-[#49454F]">
                  Roda no seu navegador. Nenhum dado pessoal é coletado.
                </div>
              </div>
            )}

            {phase === 'running' && (
              <div className="text-center">
                <div className="animate-pulse text-[16px] font-medium text-[#49454F]">Medindo estabilidade…</div>
                <div className="mt-3 flex items-baseline justify-center gap-2.5 font-mono text-[64px] font-bold leading-none tracking-[-3px]">
                  {live}
                  <span className="text-[20px] font-medium text-[#49454F]">ms</span>
                </div>
                <div className="mx-auto mt-6 h-[6px] max-w-[320px] overflow-hidden rounded-full bg-[#F3EEFA]">
                  <div style={{ width: `${pct}%` }} className="h-full rounded-full bg-[#5B21D6] transition-all" />
                </div>
                <button
                  type="button"
                  onClick={cancelTest}
                  className="mt-6 cursor-pointer rounded-full border-0 bg-[#F3EEFA] px-6 py-3 text-[15px] font-medium text-[#210A5C]"
                >
                  Cancelar
                </button>
              </div>
            )}

            {phase === 'done' && (
              <div>
                <div className="text-[12px] font-medium text-[#49454F]">Resultado</div>
                <div className="mt-2 text-[28px] font-bold leading-[34px] tracking-[-0.5px]">{verdict}</div>
                <p className="mt-2 text-[16px] leading-6 text-[#49454F]">{sub}</p>
                <div className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(120px,1fr))] gap-4 border-t border-[#E7E0EC] pt-4">
                  {metrics.map((m) => (
                    <div key={m.l}>
                      <div className="text-[12px] font-medium text-[#49454F]">{m.l}</div>
                      <div className="mt-1 text-[28px] font-bold tracking-[-1px]">
                        {m.v}
                        <span className="text-[14px] font-medium text-[#49454F]"> {m.u}</span>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-[#EAE0FF] p-4">
                  <span className="max-w-[18em] text-[15px] leading-[22px] text-[#210A5C]">
                    Quer o veredito para o seu jogo e o que fazer?
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
                  onClick={runTest}
                  className="mt-3 cursor-pointer border-0 bg-transparent py-1.5 text-[15px] font-semibold text-[#5B21D6]"
                >
                  Testar de novo
                </button>
              </div>
            )}

            {phase === 'error' && (
              <div className="text-center">
                <div className="text-[24px] font-bold">Não conseguimos medir.</div>
                <p className="mx-auto mt-2.5 max-w-[22em] text-[15px] leading-[22px] text-[#49454F]">
                  Verifique se você está conectado e tente de novo.
                </p>
                <button
                  type="button"
                  onClick={runTest}
                  className="mt-5 cursor-pointer rounded-full border-0 bg-[#5B21D6] px-7 py-3.5 text-[16px] font-medium text-white"
                >
                  Tentar de novo
                </button>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="bg-[#F8F5FB] px-6 py-24 text-[#1C1B1F]">
        <div className="mx-auto max-w-[760px]">
          <h2 className="m-0 mb-8 text-[clamp(28px,3.4vw,40px)] font-bold leading-[1.1] tracking-[-1px]">
            Perguntas comuns
          </h2>
          {FAQ.map((item) => (
            <details key={item.q} className="group border-t border-[#D9D2E3] py-5">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[18px] font-semibold leading-6 [&::-webkit-details-marker]:hidden">
                <span>{item.q}</span>
                <span className="text-[26px] font-normal leading-none text-[#5B21D6] transition-transform duration-150 group-open:rotate-45">
                  +
                </span>
              </summary>
              <p className="m-0 mt-3 max-w-[36em] text-[16px] leading-6 text-[#49454F]">{item.a}</p>
            </details>
          ))}
        </div>
      </section>

      {/* Closing dark CTA */}
      <section className="bg-[#210A5C] px-6 py-24">
        <div className="mx-auto grid max-w-[1120px] grid-cols-[repeat(auto-fit,minmax(min(280px,100%),1fr))] items-center gap-x-14 gap-y-8">
          <h2 className="m-0 max-w-[16em] text-balance text-[clamp(32px,4.2vw,52px)] font-bold leading-[1.05] tracking-[-1.2px] text-white">
            Descubra antes da fila, não no meio da partida.
          </h2>
          <div>
            <p className="m-0 text-[19px] leading-[29px] text-[#E7DEFF]">
              Baixe o SignallQ, abra o Modo Jogos e faça o primeiro check em menos de um minuto.
            </p>
            <Link
              href="/#baixar"
              className="mt-7 inline-block rounded-full bg-[#EAE0FF] px-8 py-[18px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white"
            >
              Baixar grátis no Google Play
            </Link>
            <div className="mt-3.5 text-[14px] text-[#E7DEFF]">Sem cadastro para começar</div>
          </div>
        </div>
      </section>
    </div>
  );
}
