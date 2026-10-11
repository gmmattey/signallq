// Configuração central do site público SignallQ.
// Nenhum valor sensível é exposto aqui além de identificadores públicos
// (publisher id do AdSense) — nada de segredos (INGEST_KEY nunca entra aqui,
// vive só no Pages Function server-side, ver functions/api/track.ts).
//
// `process.env.NEXT_PUBLIC_*` permite configurar por ambiente sem editar código
// (ex.: variável de ambiente do projeto na Vercel) — sem override, os defaults
// abaixo mandam o site mostrar estados claros de "ainda não configurado" em vez
// de link quebrado ou anúncio vazio.

// Ficha pública do app na Play Store — destino do CTA de download da landing.
export const SIGNALLQ_PLAY_STORE_URL: string =
  process.env.NEXT_PUBLIC_SIGNALLQ_PLAY_STORE_URL ||
  'https://play.google.com/store/apps/details?id=io.signallq.app'

// Proxy server-side de telemetria (Pages Function) — nunca chama o admin-worker
// direto do navegador (exigiria expor a INGEST_KEY no client).
export const TELEMETRY_ENDPOINT = '/api/track'

// Motor do teste de velocidade em /teste-de-velocidade — chamado direto do
// navegador (sem segredo envolvido, por isso NEXT_PUBLIC_*). Default aponta
// pro mesmo endpoint público usado pelo protótipo; só sobrescreva se for
// trocar por um endpoint de medição próprio.
export const SPEEDTEST_DOWNLOAD_URL: string =
  process.env.NEXT_PUBLIC_SPEEDTEST_DOWNLOAD_URL || 'https://speed.cloudflare.com/__down'
export const SPEEDTEST_UPLOAD_URL: string =
  process.env.NEXT_PUBLIC_SPEEDTEST_UPLOAD_URL || 'https://speed.cloudflare.com/__up'
export const SPEEDTEST_META_URL: string =
  process.env.NEXT_PUBLIC_SPEEDTEST_META_URL || 'https://speed.cloudflare.com/meta'

// Produtos do ecossistema SignallQ (configuráveis de forma centralizada)
export const LAGCHECK_URL: string =
  process.env.NEXT_PUBLIC_LAGCHECK_URL || 'https://lagcheck.signallq.com'
export const LINKA_URL: string =
  process.env.NEXT_PUBLIC_LINKA_URL || 'https://linka.signallq.com'
export const WIFICASA_URL: string =
  process.env.NEXT_PUBLIC_WIFICASA_URL || '/mapa-de-calor-wifi'

