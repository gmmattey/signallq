"use client";

import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { type GameGenre } from "./modoGamerEngine";
import { ModoGamerTestSection } from "./ModoGamerTestSection";
import { AppScreenshot } from "@/components/AppScreenshot";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SIGNALLQ_PLAY_STORE_URL } from "@/lib/config";
import { FEATURE_DOWNLOAD_APP_CLICADO, trackFeatureUsed } from "@/lib/telemetry";

import { DEVICES, FAQ, GAMES_DATA, GENRES } from "./modoGamerData";

export default function ModoGamerContent() {
  const [selectedGame, setSelectedGame] = useState('Fortnite');
  const [selectedGenre, setSelectedGenre] = useState('Todos');
  const [customGenre, setCustomGenre] = useState<GameGenre>('FPS');

  const filteredGames = GAMES_DATA.filter(
    (g) => selectedGenre === 'Todos' || g.genre === selectedGenre || g.id === 'custom'
  );

  const selectedGameEntry = GAMES_DATA.find((g) => g.name === selectedGame);
  const selectedGenreOfGame: GameGenre =
    selectedGame === 'Meu jogo não está na lista'
      ? customGenre
      : (selectedGameEntry?.genre || 'FPS');

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
            Modo Jogos · Exclusivo no app SignallQ
          </div>

          <h1 className="m-0 mt-5 text-balance text-[clamp(40px,5.6vw,68px)] font-bold leading-tight tracking-[-1.8px]">
            Sua conexão está adequada para jogar?
          </h1>

          <p className="mt-6 max-w-[30em] text-pretty text-[18px] sm:text-[20px] leading-[30px] text-[#CAC4D0]">
            Velocidade alta não evita lag e travamentos. O SignallQ avalia os indicadores essenciais da sua conexão — latência, jitter, perda de pacotes e estabilidade sob carga — e estima a adequação ao perfil de cada jogo.
          </p>

          <div className="mt-8 flex flex-wrap gap-3">
            <a
              href={SIGNALLQ_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO)}
              className="rounded-full bg-[#EAE0FF] px-[30px] py-[17px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors shadow-[0_4px_16px_rgba(234,224,255,0.3)]"
            >
              Baixar no Google Play
            </a>
            <a
              href="#teste"
              className="rounded-full border border-[#4A4458] px-7 py-4 text-[17px] font-medium text-[#F5F2F7] no-underline hover:border-[#C9B6FF] transition-colors"
            >
              Simular teste na web
            </a>
          </div>

          <div className="mt-5 text-[13px] text-[#A9A3B5] leading-relaxed">
            Grátis · Sem cadastro · Precisa de diagnóstico profundo de rotas de PC?{" "}
            <Link href="/#outras-solucoes" className="text-[#C9B6FF] underline font-medium">
              Conheça o Lagcheck
            </Link>
          </div>
        </div>

        {/* Captura real do app com resultado de Dead by Daylight */}
        <div className="flex justify-center">
          <AppScreenshot
            screenshot={APPROVED_SCREENSHOTS.modoGamer}
            priority
            maxWidth={320}
          />
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
            <div className="text-[15px] text-[#A9A3B5]">25 títulos e 6 categorias genéricas no app</div>
          </div>

          <div className="mt-7 flex flex-wrap gap-2">
            {GENRES.map((g) => (
              <button
                key={g}
                type="button"
                aria-pressed={selectedGenre === g}
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

          <div className="mt-7 grid grid-cols-[repeat(auto-fill,minmax(min(176px,100%),1fr))] gap-3.5">
            {filteredGames.map((game) => {
              const sel = selectedGame === game.name;
              return (
                <button
                  key={game.id}
                  type="button"
                  aria-pressed={sel}
                  onClick={() => setSelectedGame(game.name)}
                  className={`group flex cursor-pointer flex-col gap-3 rounded-3xl border p-3.5 text-left transition-all overflow-hidden ${
                    sel
                      ? 'border-[#7B4DF0] bg-[#1D1535] shadow-[0_8px_24px_-4px_rgba(123,77,240,0.35)] ring-1 ring-[#7B4DF0]'
                      : 'border-[#262B33] bg-[#14171C] hover:border-[#7B4DF0] hover:bg-[#181C22]'
                  }`}
                >
                  <div className="relative aspect-[16/10] w-full overflow-hidden rounded-2xl bg-[#1F242C]">
                    <Image
                      src={game.img}
                      alt=""
                      fill
                      sizes="(max-width: 768px) 100vw, 200px"
                      className="object-cover transition-transform duration-300 group-hover:scale-105"
                      loading="lazy"
                      unoptimized
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-40" />
                  </div>
                  <div>
                    <div className="text-[15px] font-bold leading-5 text-[#F5F2F7] truncate">{game.name}</div>
                    <div className="mt-0.5 text-[12px] font-medium text-[#A9A3B5]">{game.genre}</div>
                  </div>
                  <div className={`mt-auto flex items-center gap-1.5 text-[11px] font-semibold ${sel ? 'text-[#C9B6FF]' : 'text-[#7DDB92]'}`}>
                    <span className={`h-1.5 w-1.5 rounded-full ${sel ? 'bg-[#C9B6FF]' : 'bg-[#7DDB92]'}`} />
                    {sel ? 'Selecionado' : 'Perfil calibrado'}
                  </div>
                </button>
              );
            })}
          </div>

          {selectedGame === 'Meu jogo não está na lista' && (
            <div className="mt-6 rounded-2xl border border-[#7B4DF0]/40 bg-[#1D1535] p-5">
              <div className="text-[14px] font-semibold text-white">
                Qual é o gênero do seu jogo?
              </div>
              <p className="mt-1 text-[13px] text-[#CAC4D0]">
                O teste ajustará a régua de sensibilidade (latência aceitável, jitter e tolerância a bufferbloat) para essa categoria.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                {(['FPS', 'MOBA', 'Battle royale', 'Esportes', 'Casual'] as GameGenre[]).map((gen) => (
                  <button
                    key={gen}
                    type="button"
                    onClick={() => setCustomGenre(gen)}
                    className={`rounded-full px-4 py-1.5 text-[13px] font-medium transition-colors ${
                      customGenre === gen
                        ? 'bg-[#7B4DF0] text-white font-semibold'
                        : 'border border-[#3A3446] bg-[#14171C] text-[#CAC4D0] hover:border-[#C9B6FF]'
                    }`}
                  >
                    {gen}
                  </button>
                ))}
              </div>
            </div>
          )}

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
              No app SignallQ, a sonda UDP nativa mede a rota até o beacon regional da AWS. Na web, o teste mede a rota Anycast da Cloudflare. É uma amostra confiável da sua conexão, não o servidor específico do seu jogo.
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
                Catálogo de 25 títulos ou 6 categorias genéricas. PlayStation, Xbox, PC, Android, iPhone, Switch ou TV.
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
              <div className="text-[20px] font-bold text-white">Modo Jogos no App SignallQ</div>
              <p className="mt-2 text-[15px] leading-[22px] text-[#E7DEFF]">Mede o que pesa para jogar naquela rota.</p>
              <div className="mt-5 flex flex-col gap-2.5 text-[16px] font-medium text-white">
                <span>Tempo de resposta</span>
                <span>Jitter (variação)</span>
                <span>Perda de pacotes (UDP nativo)</span>
                <span>Sinal e canais Wi-Fi</span>
                <span>Veredito para o seu jogo</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Interactive web test */}
      <ModoGamerTestSection
        selectedGame={selectedGame}
        selectedGenre={selectedGenreOfGame}
      />

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
            <a
              href={SIGNALLQ_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO)}
              className="mt-7 inline-block rounded-full bg-[#EAE0FF] px-8 py-[18px] text-[17px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors"
            >
              Baixar grátis no Google Play
            </a>
            <div className="mt-3.5 text-[14px] text-[#E7DEFF]">Sem cadastro para começar</div>
          </div>
        </div>
      </section>
    </div>
  );
}

