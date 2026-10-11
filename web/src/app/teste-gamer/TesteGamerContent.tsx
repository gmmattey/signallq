"use client";

import { useId, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { SIGNALLQ_PLAY_STORE_URL } from "@/lib/config";
import {
  FEATURE_DOWNLOAD_APP_CLICADO,
  FEATURE_TESTE_GAMER_CONCLUIDO,
  FEATURE_TESTE_GAMER_ERRO,
  FEATURE_TESTE_GAMER_INICIADO,
  FEATURE_TESTE_GAMER_JOGO_SELECIONADO,
  trackFeatureUsed,
} from "@/lib/telemetry";
import { GAMES_DATA, POPULAR_GAMES } from "../modo-gamer/modoGamerData";
import {
  type GameGenre,
  type GamerVerdictStatus,
  type ScoreBand,
} from "../modo-gamer/modoGamerEngine";
import { useModoGamerRunner } from "../modo-gamer/useModoGamerRunner";

const GENRES_LIST: { id: GameGenre; label: string; desc: string }[] = [
  { id: 'FPS', label: 'FPS / Tiro', desc: 'Exige latência ultra-baixa e zero jitter' },
  { id: 'Battle royale', label: 'Battle Royale', desc: 'Sensível a variações em tiroteios e zonas' },
  { id: 'MOBA', label: 'MOBA', desc: 'Requer registro consistente de habilidades' },
  { id: 'Esportes', label: 'Esportes / Corrida', desc: 'Exige resposta ágil em disputas 1v1' },
  { id: 'Casual', label: 'Casual / Outros', desc: 'Maior tolerância a pequenas oscilações' },
];

function getVerdictBadgeStyle(status: GamerVerdictStatus) {
  switch (status) {
    case 'adequate':
      return {
        bg: 'bg-[#143B22]',
        border: 'border-[#7DDB92]/40',
        text: 'text-[#7DDB92]',
        icon: '✓',
      };
    case 'warning':
      return {
        bg: 'bg-[#3D2808]',
        border: 'border-[#FFB867]/40',
        text: 'text-[#FFB867]',
        icon: '!',
      };
    case 'unstable':
      return {
        bg: 'bg-[#3A1412]',
        border: 'border-[#FF8A7A]/40',
        text: 'text-[#FF8A7A]',
        icon: '✕',
      };
    case 'inconclusive':
    default:
      return {
        bg: 'bg-[#262B33]',
        border: 'border-[#4A4458]',
        text: 'text-[#CAC4D0]',
        icon: '?',
      };
  }
}

function getGradeColor(grade: ScoreBand) {
  switch (grade) {
    case 'A+':
    case 'A':
      return 'text-[#7DDB92] border-[#7DDB92]/40 bg-[#7DDB92]/10';
    case 'B':
      return 'text-[#B5E77E] border-[#B5E77E]/40 bg-[#B5E77E]/10';
    case 'C':
      return 'text-[#FFB867] border-[#FFB867]/40 bg-[#FFB867]/10';
    case 'D':
    case 'F':
    default:
      return 'text-[#FF8A7A] border-[#FF8A7A]/40 bg-[#FF8A7A]/10';
  }
}

export default function TesteGamerContent() {
  const searchInputId = useId();
  const [selectedGame, setSelectedGame] = useState<string | null>(null);
  const [selectedGenre, setSelectedGenre] = useState<GameGenre>('FPS');
  const [searchQuery, setSearchQuery] = useState('');
  const [isCustomMode, setIsCustomMode] = useState(false);

  const { state: runner, startTest, cancelTest } = useModoGamerRunner();
  const resultHeadingRef = useRef<HTMLHeadingElement>(null);
  const errorHeadingRef = useRef<HTMLHeadingElement>(null);

  // Filtro de jogos pelo termo de busca
  const searchResults = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return [];
    return GAMES_DATA.filter(
      (g) => g.id !== 'custom' && g.name.toLowerCase().includes(q)
    );
  }, [searchQuery]);

  const handleSelectGame = (name: string, genre: GameGenre) => {
    setSelectedGame(name);
    setSelectedGenre(genre);
    setIsCustomMode(false);
    setSearchQuery('');
    trackFeatureUsed(FEATURE_TESTE_GAMER_JOGO_SELECIONADO);
  };

  const handleSelectCustomGenre = (genre: GameGenre) => {
    setSelectedGame(`Jogo (${genre})`);
    setSelectedGenre(genre);
    setIsCustomMode(true);
    setSearchQuery('');
    trackFeatureUsed(FEATURE_TESTE_GAMER_JOGO_SELECIONADO);
  };

  const handleStartTest = () => {
    if (!selectedGame) return;
    trackFeatureUsed(FEATURE_TESTE_GAMER_INICIADO);
    startTest(selectedGame, selectedGenre).then(() => {
      trackFeatureUsed(FEATURE_TESTE_GAMER_CONCLUIDO);
    }).catch(() => {
      trackFeatureUsed(FEATURE_TESTE_GAMER_ERRO);
    });
  };

  const handleResetGameChoice = () => {
    cancelTest();
    setSelectedGame(null);
    setIsCustomMode(false);
    setSearchQuery('');
  };

  const handleDownloadClick = () => {
    trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO);
  };

  // Anúncio acessível para leitores de tela
  let liveAnnouncement = "";
  if (runner.phase === 'locating') {
    liveAnnouncement = "Localizando ponto de conexão regional.";
  } else if (runner.phase === 'measuring_ping') {
    liveAnnouncement = "Medindo tempo de resposta e estabilidade.";
  } else if (runner.phase === 'measuring_upload') {
    liveAnnouncement = "Analisando a conexão sob carga e bufferbloat.";
  } else if (runner.phase === 'done' && runner.result) {
    liveAnnouncement = `Teste concluído: ${runner.result.verdict}. ${runner.result.detail}`;
  } else if (runner.phase === 'error') {
    liveAnnouncement = `Erro na medição: ${runner.errorMsg || 'Falha ao medir conexão.'}`;
  }

  const isRunning =
    runner.phase === 'locating' ||
    runner.phase === 'measuring_ping' ||
    runner.phase === 'measuring_upload';

  return (
    <div className="min-h-screen bg-[#0B0D10] font-sans leading-normal text-[#F5F2F7]">
      {/* Live Region para A11y */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">
        {liveAnnouncement}
      </div>

      <div className="mx-auto max-w-[860px] px-4 py-8 sm:px-6 sm:py-12">
        {/* Navegação contextual superior */}
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 text-[14px]">
          <Link
            href="/modo-gamer"
            className="inline-flex items-center gap-1.5 font-medium text-[#CAC4D0] no-underline hover:text-white transition-colors"
          >
            ← Conhecer o Modo Gamer no app
          </Link>
          <div className="inline-flex items-center gap-2 rounded-full border border-[#3A3446] bg-[#14171C] px-3 py-1 text-[12px] font-semibold text-[#C9B6FF]">
            <span className="h-2 w-2 rounded-full bg-[#7DDB92]" aria-hidden="true" />
            Teste no navegador
          </div>
        </div>

        {/* ── ETAPA 1: ESCOLHER O JOGO (IDLE) ────────────────────────── */}
        {runner.phase === 'idle' && (
          <div className="rounded-[32px] border border-[#262B33] bg-[#14171C] p-6 sm:p-10 shadow-2xl">
            <div className="text-center">
              <h1 className="m-0 text-[clamp(28px,4.5vw,44px)] font-bold tracking-tight text-white leading-tight">
                Teste sua conexão para jogar
              </h1>
              <p className="mx-auto mt-3 max-w-[34em] text-[16px] sm:text-[18px] text-[#CAC4D0] leading-relaxed">
                Escolha seu jogo e descubra como está a estabilidade da sua conexão.
              </p>
            </div>

            {/* Aviso técnico transparente */}
            <div className="mt-6 rounded-2xl border border-[#262B33] bg-[#0E1116] px-4 py-3 text-center text-[12px] text-[#A9A3B5] leading-relaxed">
              Medição via HTTP/HTTPS no navegador até o ponto Anycast regional. Não mede o servidor oficial do jogo nem perda física UDP.
            </div>

            {/* Seleção ativa em destaque */}
            {selectedGame ? (
              <div className="mt-8 rounded-2xl border border-[#7B4DF0] bg-[#1D1535] p-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="text-[12px] font-semibold uppercase tracking-wider text-[#C9B6FF]">
                      Jogo selecionado
                    </div>
                    <div className="mt-1 text-[22px] font-bold text-white">
                      {selectedGame}
                    </div>
                    <div className="mt-1 text-[13px] text-[#E7DEFF]">
                      Régua de sensibilidade ajustada para a categoria <strong>{selectedGenre}</strong>.
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedGame(null);
                      setIsCustomMode(false);
                    }}
                    className="cursor-pointer rounded-full border border-[#4A4458] bg-[#14171C] px-4 py-2 text-[13px] font-medium text-[#CAC4D0] hover:border-[#C9B6FF] hover:text-white transition-colors"
                  >
                    Trocar jogo
                  </button>
                </div>
              </div>
            ) : (
              <div className="mt-8 space-y-6">
                {/* Campo de Busca */}
                <div>
                  <label
                    htmlFor={searchInputId}
                    className="block text-[14px] font-semibold text-[#E7E0EC] mb-2"
                  >
                    Buscar título do jogo
                  </label>
                  <div className="relative">
                    <input
                      id={searchInputId}
                      type="search"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Buscar jogo (ex: Valorant, CS2, Fortnite, League of Legends...)"
                      className="w-full rounded-2xl border border-[#3A3446] bg-[#0E1116] px-4 py-3.5 text-[15px] text-white placeholder-[#79747E] focus:border-[#7B4DF0] focus:outline-none focus:ring-2 focus:ring-[#7B4DF0]/40 transition-all"
                    />
                    {searchQuery && (
                      <button
                        type="button"
                        onClick={() => setSearchQuery('')}
                        aria-label="Limpar busca"
                        className="absolute right-3.5 top-1/2 -translate-y-1/2 text-[14px] text-[#CAC4D0] hover:text-white"
                      >
                        ✕
                      </button>
                    )}
                  </div>

                  {/* Resultados da Busca */}
                  {searchQuery && (
                    <div className="mt-3 rounded-2xl border border-[#262B33] bg-[#0E1116] p-3 max-h-60 overflow-y-auto">
                      {searchResults.length > 0 ? (
                        <div className="flex flex-col gap-1">
                          {searchResults.map((game) => (
                            <button
                              key={game.id}
                              type="button"
                              onClick={() => handleSelectGame(game.name, game.genre)}
                              className="flex items-center justify-between rounded-xl px-3 py-2.5 text-left text-[14px] text-white hover:bg-[#1D1535] hover:text-[#C9B6FF] transition-colors"
                            >
                              <span className="font-semibold">{game.name}</span>
                              <span className="text-[12px] text-[#A9A3B5]">{game.genre}</span>
                            </button>
                          ))}
                        </div>
                      ) : (
                        <div className="p-3 text-center">
                          <p className="text-[13px] text-[#CAC4D0]">
                            Jogo não encontrado no catálogo. Escolha a categoria abaixo para calibrar o teste:
                          </p>
                          <div className="mt-3 flex flex-wrap justify-center gap-2">
                            {GENRES_LIST.map((g) => (
                              <button
                                key={g.id}
                                type="button"
                                onClick={() => handleSelectCustomGenre(g.id)}
                                className="rounded-full border border-[#3A3446] bg-[#14171C] px-3.5 py-1.5 text-[12px] font-medium text-[#E7E0EC] hover:border-[#7B4DF0] hover:text-white transition-colors"
                              >
                                {g.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Jogos Populares */}
                <div>
                  <div className="text-[13px] font-semibold text-[#CAC4D0] mb-3">
                    Jogos populares
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {POPULAR_GAMES.map((name) => {
                      const entry = GAMES_DATA.find((g) => g.name === name);
                      const genre = entry ? entry.genre : 'FPS';
                      return (
                        <button
                          key={name}
                          type="button"
                          onClick={() => handleSelectGame(name, genre)}
                          className="rounded-full border border-[#3A3446] bg-[#0E1116] px-4 py-2 text-[14px] font-medium text-[#E7E0EC] hover:border-[#7B4DF0] hover:bg-[#1D1535] hover:text-white transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF]"
                        >
                          {name}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Alternativa: Meu jogo não está na lista */}
                <div className="border-t border-[#262B33] pt-5">
                  <button
                    type="button"
                    onClick={() => setIsCustomMode((prev) => !prev)}
                    className="inline-flex items-center gap-2 text-[14px] font-semibold text-[#C9B6FF] hover:underline"
                  >
                    <span>{isCustomMode ? '▼' : '▶'}</span>
                    Meu jogo não está na lista (escolher por categoria)
                  </button>

                  {isCustomMode && (
                    <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                      {GENRES_LIST.map((g) => (
                        <button
                          key={g.id}
                          type="button"
                          onClick={() => handleSelectCustomGenre(g.id)}
                          className="rounded-2xl border border-[#3A3446] bg-[#0E1116] p-3 text-left hover:border-[#7B4DF0] hover:bg-[#1D1535] transition-colors"
                        >
                          <div className="text-[14px] font-bold text-white">{g.label}</div>
                          <div className="mt-1 text-[12px] text-[#A9A3B5]">{g.desc}</div>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* CTA Iniciar Teste */}
            <div className="mt-10 text-center">
              <button
                type="button"
                onClick={handleStartTest}
                disabled={!selectedGame}
                className={`w-full sm:w-auto rounded-full px-10 py-4 text-[17px] font-semibold transition-all shadow-lg ${
                  selectedGame
                    ? 'cursor-pointer bg-[#7B4DF0] text-white hover:bg-[#8D64F5] hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C]'
                    : 'cursor-not-allowed bg-[#262B33] text-[#79747E]'
                }`}
              >
                {selectedGame ? `Iniciar teste para ${selectedGame}` : 'Escolha um jogo acima para iniciar'}
              </button>

              <div className="mt-4 text-[13px] text-[#A9A3B5]">
                Duração estimada de 10 a 15 segundos · Sem downloads de arquivos grandes
              </div>
            </div>
          </div>
        )}

        {/* ── ETAPA 2: EXECUTAR A MEDIÇÃO (RUNNING) ────────────────────── */}
        {isRunning && (
          <div className="rounded-[32px] border border-[#262B33] bg-[#14171C] p-6 sm:p-10 text-center shadow-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#3A3446] bg-[#0E1116] px-3.5 py-1 text-[13px] font-semibold text-[#C9B6FF]">
              Testando para <strong className="text-white">{selectedGame}</strong> ({selectedGenre})
            </div>

            {/* Mensagem simples da fase */}
            <div className="mt-6 text-[18px] sm:text-[20px] font-bold text-white">
              {runner.phase === 'locating' && 'Localizando ponto de conexão regional...'}
              {runner.phase === 'measuring_ping' && 'Medindo tempo de resposta e estabilidade...'}
              {runner.phase === 'measuring_upload' && 'Analisando a conexão sob carga...'}
            </div>

            {/* Valor ao vivo */}
            <div className="mt-4 flex items-baseline justify-center gap-2 font-mono text-[64px] sm:text-[80px] font-bold leading-none tracking-[-3px] text-white">
              {runner.liveValue}
              <span className="text-[20px] font-medium text-[#CAC4D0]"> {runner.liveUnit}</span>
            </div>

            {/* Barra de progresso */}
            <div
              role="progressbar"
              aria-valuenow={runner.progressPct}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Progresso do teste de conexão gamer"
              className="mx-auto mt-6 h-2.5 max-w-[420px] overflow-hidden rounded-full bg-[#0E1116]"
            >
              <div
                style={{ width: `${runner.progressPct}%` }}
                className="h-full rounded-full bg-gradient-to-r from-[#5B21D6] to-[#7B4DF0] transition-all duration-200"
              />
            </div>

            {/* Chips de métricas parciais */}
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3 text-[13px] text-[#CAC4D0]">
              <div className="rounded-xl border border-[#262B33] bg-[#0E1116] px-3.5 py-2">
                Tempo de resposta:{' '}
                <span className="font-mono font-semibold text-white">
                  {runner.idlePing != null ? `${runner.idlePing} ms` : '—'}
                </span>
              </div>
              <div className="rounded-xl border border-[#262B33] bg-[#0E1116] px-3.5 py-2">
                Jitter (MAD):{' '}
                <span className="font-mono font-semibold text-white">
                  {runner.jitter != null ? `${runner.jitter} ms` : '—'}
                </span>
              </div>
              <div className="rounded-xl border border-[#262B33] bg-[#0E1116] px-3.5 py-2">
                Atraso sob carga:{' '}
                <span className="font-mono font-semibold text-white">
                  {runner.bufferbloat != null ? `+${runner.bufferbloat} ms` : '—'}
                </span>
              </div>
            </div>

            <div className="mt-8">
              <button
                type="button"
                onClick={cancelTest}
                className="cursor-pointer rounded-full border border-[#4A4458] bg-transparent px-6 py-2.5 text-[14px] font-medium text-[#CAC4D0] hover:border-red-500/60 hover:text-white transition-colors"
              >
                Cancelar teste
              </button>
            </div>

            <div className="mt-4 text-[12px] text-[#A9A3B5]">
              Medição direta no navegador · Rota Anycast regional
            </div>
          </div>
        )}

        {/* ── ETAPA 3: RESULTADO (DONE) ────────────────────────────────── */}
        {runner.phase === 'done' && runner.result && (
          <div className="rounded-[32px] border border-[#262B33] bg-[#14171C] p-6 sm:p-10 shadow-2xl">
            {/* 1. Veredito Principal em Linguagem Comum */}
            <div className="border-b border-[#262B33] pb-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span className="text-[13px] text-[#CAC4D0]">
                  Avaliação para <strong className="text-white">{selectedGame}</strong> ({selectedGenre})
                </span>
                {/* Nota Secundária como Estimativa */}
                <span
                  className={`inline-flex items-center rounded-xl border px-3 py-1 font-mono text-[14px] font-bold ${getGradeColor(
                    runner.result.grade
                  )}`}
                >
                  Estimativa: {runner.result.overallScore}/100 · Grau {runner.result.grade}
                </span>
              </div>

              {/* Veredito Principal */}
              <div className="mt-4 flex items-center gap-3">
                {(() => {
                  const style = getVerdictBadgeStyle(runner.result.verdictStatus);
                  return (
                    <div
                      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border font-bold text-[18px] ${style.bg} ${style.border} ${style.text}`}
                      aria-hidden="true"
                    >
                      {style.icon}
                    </div>
                  );
                })()}
                <h2
                  ref={resultHeadingRef}
                  tabIndex={-1}
                  className="m-0 text-[26px] sm:text-[32px] font-bold text-white tracking-tight leading-tight outline-none"
                >
                  {runner.result.verdict}
                </h2>
              </div>

              {/* 2. Explicação concisa */}
              <p className="mt-3 max-w-[42em] text-[16px] sm:text-[17px] leading-relaxed text-[#CAC4D0]">
                {runner.result.detail}
              </p>
            </div>

            {/* 3. Principal Indicador que Merece Atenção */}
            <div className="my-6 rounded-2xl border border-[#262B33] bg-[#0E1116] p-5">
              <div className="text-[12px] font-semibold uppercase tracking-wider text-[#C9B6FF]">
                Principal indicador observado
              </div>
              <div className="mt-1 text-[16px] font-semibold text-white">
                {runner.result.primaryFactor}
              </div>

              {/* 4. O que pode estar afetando sua conexão (Hipótese fundamentada) */}
              <div className="mt-4 pt-4 border-t border-[#1F242C]">
                <div className="text-[12px] font-semibold uppercase tracking-wider text-[#CAC4D0]">
                  O que pode estar afetando sua conexão
                </div>
                <div className="mt-1 text-[14px] text-[#E7E0EC] leading-relaxed">
                  {runner.result.culpritHypothesis}
                </div>
              </div>
            </div>

            {/* 5. Recomendações Práticas */}
            <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-5">
              <div className="text-[14px] font-bold text-white">
                Recomendações práticas para sua partida:
              </div>
              <ul className="mt-2.5 space-y-2 pl-4 text-[14px] text-[#CAC4D0] list-disc">
                {runner.result.actions.map((act, idx) => (
                  <li key={idx} className="leading-relaxed">
                    {act}
                  </li>
                ))}
              </ul>
            </div>

            {/* 6. Métricas Detalhadas em Área Expansível */}
            <div className="mt-6">
              <details className="group rounded-2xl border border-[#262B33] bg-[#0E1116] p-4 text-[14px] text-[#CAC4D0]">
                <summary className="flex cursor-pointer list-none items-center justify-between font-semibold text-white hover:text-[#C9B6FF] [&::-webkit-details-marker]:hidden">
                  <span>Ver medições detalhadas da conexão</span>
                  <span className="text-[18px] text-[#C9B6FF] transition-transform duration-150 group-open:rotate-180">
                    ▼
                  </span>
                </summary>

                <div className="mt-5 space-y-4 pt-4 border-t border-[#1F242C]">
                  {/* Grid das métricas */}
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-5 text-left">
                    <div className="rounded-xl border border-[#262B33] bg-[#14171C] p-3">
                      <div className="text-[11px] text-[#CAC4D0]">Tempo de resposta</div>
                      <div className="mt-1 font-mono text-[18px] font-bold text-white">
                        {runner.idlePing} <span className="text-[11px] font-normal text-[#CAC4D0]">ms</span>
                      </div>
                      <div className="mt-1 text-[11px] text-[#7DDB92]">
                        Nota {runner.result.pingScore}/100
                      </div>
                    </div>

                    <div className="rounded-xl border border-[#262B33] bg-[#14171C] p-3">
                      <div className="text-[11px] text-[#CAC4D0]">Jitter (MAD)</div>
                      <div className="mt-1 font-mono text-[18px] font-bold text-white">
                        {runner.jitter} <span className="text-[11px] font-normal text-[#CAC4D0]">ms</span>
                      </div>
                      <div className="mt-1 text-[11px] text-[#7DDB92]">
                        Nota {runner.result.jitScore}/100
                      </div>
                    </div>

                    <div className="rounded-xl border border-[#262B33] bg-[#14171C] p-3">
                      <div className="text-[11px] text-[#CAC4D0]">Atraso sob carga</div>
                      <div className="mt-1 font-mono text-[18px] font-bold text-white">
                        {runner.bufferbloat != null ? `+${runner.bufferbloat}` : '—'} <span className="text-[11px] font-normal text-[#CAC4D0]">ms</span>
                      </div>
                      <div className="mt-1 text-[11px] text-[#CAC4D0]">
                        {runner.result.bloatScore != null ? `Nota ${runner.result.bloatScore}/100` : 'Sem saturação'}
                      </div>
                    </div>

                    <div className="rounded-xl border border-[#262B33] bg-[#14171C] p-3">
                      <div className="text-[11px] text-[#CAC4D0]">Vazão de upload</div>
                      <div className="mt-1 font-mono text-[18px] font-bold text-white">
                        {runner.uploadMbps != null ? runner.uploadMbps : '—'} <span className="text-[11px] font-normal text-[#CAC4D0]">Mbps</span>
                      </div>
                      <div className="mt-1 text-[11px] text-[#CAC4D0]">
                        {runner.result.uploadScore != null ? `Nota ${runner.result.uploadScore}/100` : 'Sem upload'}
                      </div>
                    </div>

                    <div className="rounded-xl border border-[#262B33] bg-[#14171C] p-3">
                      <div className="text-[11px] text-[#CAC4D0]">Perda UDP</div>
                      <div className="mt-1 text-[14px] font-bold text-[#E7E0EC]">Não medido</div>
                      <div className="mt-1 text-[10px] text-[#CAC4D0]">Exige app nativo</div>
                    </div>
                  </div>

                  {/* Borda Regional */}
                  <div className="text-[12px] text-[#CAC4D0]">
                    Ponto regional de referência:{' '}
                    <strong className="text-white">
                      {runner.edgeInfo?.city ? `${runner.edgeInfo.city} (${runner.edgeInfo.colo || 'BR'})` : 'Borda Anycast'}
                    </strong>
                    {runner.edgeInfo?.isp ? ` · ${runner.edgeInfo.isp}` : ''}
                  </div>

                  {/* Limitações do teste web */}
                  <div className="rounded-xl bg-[#14171C] p-3 text-[12px] text-[#CAC4D0] leading-relaxed">
                    <div className="font-semibold text-white mb-1">Limitações do teste no navegador:</div>
                    {runner.result.unmeasuredGaps.map((gap, i) => (
                      <div key={i}>• {gap}</div>
                    ))}
                  </div>
                </div>
              </details>
            </div>

            {/* 7. Botões de Refazer ou Trocar Jogo */}
            <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3">
              <button
                type="button"
                onClick={handleStartTest}
                className="w-full sm:w-auto cursor-pointer rounded-full bg-[#7B4DF0] px-8 py-3.5 text-[15px] font-semibold text-white hover:bg-[#8D64F5] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF]"
              >
                Testar novamente
              </button>
              <button
                type="button"
                onClick={handleResetGameChoice}
                className="w-full sm:w-auto cursor-pointer rounded-full border border-[#4A4458] bg-[#0E1116] px-7 py-3.5 text-[15px] font-medium text-[#CAC4D0] hover:border-[#C9B6FF] hover:text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF]"
              >
                Escolher outro jogo
              </button>
            </div>

            {/* 8. CTA Contextual do SignallQ Android */}
            <div className="mt-10 rounded-2xl border border-[#7B4DF0]/40 bg-[#1D1535] p-6 text-left">
              <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
                <div className="max-w-[32em]">
                  <h3 className="m-0 text-[18px] sm:text-[20px] font-bold text-white">
                    Quer investigar melhor sua conexão?
                  </h3>
                  <p className="mt-2 text-[14px] text-[#E7DEFF] leading-relaxed">
                    O SignallQ Android também pode analisar informações locais do Wi-Fi e executar verificações adicionais para ajudar a investigar a causa da instabilidade.
                  </p>
                </div>
                <div className="flex flex-col sm:flex-row md:flex-col gap-2 shrink-0 w-full sm:w-auto">
                  <a
                    href={SIGNALLQ_PLAY_STORE_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={handleDownloadClick}
                    className="inline-flex items-center justify-center rounded-full bg-[#EAE0FF] px-6 py-3 text-[14px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors"
                  >
                    Baixar SignallQ grátis
                  </a>
                  <Link
                    href="/modo-gamer"
                    className="inline-flex items-center justify-center text-[13px] font-medium text-[#C9B6FF] hover:underline pt-1 text-center"
                  >
                    Conhecer o Modo Gamer completo no app →
                  </Link>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── TRATAMENTO DE ERRO (ERROR) ───────────────────────────────── */}
        {runner.phase === 'error' && (
          <div className="rounded-[32px] border border-red-500/30 bg-[#14171C] p-8 sm:p-12 text-center shadow-2xl">
            <div
              className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-red-500/10 text-red-400 font-bold text-[22px]"
              aria-hidden="true"
            >
              !
            </div>
            <h2
              ref={errorHeadingRef}
              tabIndex={-1}
              className="m-0 text-[24px] font-bold text-white outline-none"
            >
              Não foi possível concluir a medição
            </h2>
            <p className="mx-auto mt-3 max-w-[26em] text-[15px] text-[#CAC4D0] leading-relaxed">
              {runner.errorMsg || 'Verifique se você está conectado à internet e tente novamente.'}
            </p>

            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <button
                type="button"
                onClick={handleStartTest}
                className="cursor-pointer rounded-full bg-[#7B4DF0] px-8 py-3 text-[15px] font-semibold text-white hover:bg-[#8D64F5] transition-colors"
              >
                Tentar novamente
              </button>
              <button
                type="button"
                onClick={handleResetGameChoice}
                className="cursor-pointer rounded-full border border-[#4A4458] bg-[#0E1116] px-6 py-3 text-[15px] font-medium text-[#CAC4D0] hover:border-[#C9B6FF] hover:text-white transition-colors"
              >
                Escolher outro jogo
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
