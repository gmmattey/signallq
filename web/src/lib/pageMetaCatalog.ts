// Catálogo único de metadados por rota — consumido tanto pelo cliente
// (useDocumentMeta, upsert pós-hidratação) quanto por `functions/_middleware.ts`
// (injeção no HTML inicial via HTMLRewriter, issue #1369 Fase 2). Uma só fonte
// de verdade: mudar o title/description de uma rota aqui já reflete nos dois.
import type { PageMeta } from './seo'

export const PAGE_META: Record<string, PageMeta> = {
  '/': {
    title: 'SignallQ: descubra por que sua internet está ruim',
    description: 'App Android que analisa sua rede, mostra onde está o problema e diz o que fazer. Grátis, sem cadastro.',
    path: '/',
  },
  '/privacidade': {
    title: 'Política de Privacidade — SignallQ',
    description: 'Política de privacidade do SignallQ: dados tratados pelo app Android, armazenamento e seus direitos.',
    path: '/privacidade',
  },
  '/termos': {
    title: 'Termos de Uso — SignallQ',
    description: 'Termos de uso do aplicativo SignallQ.',
    path: '/termos',
  },
  '/como-funciona': {
    title: 'Como funciona o SignallQ | Do sintoma à solução',
    description: 'Entender, diagnosticar, resolver e confirmar: veja como o SignallQ descobre a causa da internet ruim.',
    path: '/como-funciona',
  },
  '/duvidas': {
    title: 'Dúvidas frequentes | SignallQ',
    description: 'Respostas sobre o app, o diagnóstico com IA, permissões, preço e privacidade.',
    path: '/duvidas',
  },
  '/teste-de-velocidade': {
    title: 'Teste de velocidade da internet | SignallQ',
    description: 'Meça download, upload e latência em 20 segundos e veja o resultado em palavras simples.',
    path: '/teste-de-velocidade',
  },
  '/guias': {
    title: 'Guias de internet e Wi-Fi | SignallQ',
    description: 'Guias simples para entender e resolver problemas de internet lenta, Wi-Fi fraco, latência e velocidade.',
    path: '/guias',
  },
  '/funcionalidades': {
    title: 'Funcionalidades | SignallQ',
    description: 'Teste de ping, velocidade, DNS, jitter, sinal Wi-Fi, dispositivos na rede, mapa de calor e sinal 4G/5G.',
    path: '/funcionalidades',
  },
  '/modo-gamer': {
    title: 'Internet boa para jogar online e evitar lag | Modo Jogos SignallQ',
    description: 'Descubra se sua conexão está adequada para games online. O app SignallQ analisa ping, jitter e estabilidade para indicar se a internet aguenta suas partidas sem travamentos.',
    path: '/modo-gamer',
  },
  '/teste-gamer': {
    title: 'Teste de ping e conexão para jogos online | Teste Gamer SignallQ',
    description: 'Teste de estabilidade gamer no navegador: meça latência, jitter e bufferbloat sob carga e veja se sua conexão está pronta para o seu jogo sem instalar nada.',
    path: '/teste-gamer',
  },
  '/teste-de-ping': {
    title: 'Teste de ping e latência da internet | SignallQ',
    description: 'Meça o ping da sua internet em segundos e entenda se a latência é boa para jogos, chamadas e navegação.',
    path: '/teste-de-ping',
  },
  '/teste-de-download-e-upload': {
    title: 'Teste de download e upload | SignallQ',
    description: 'Meça a velocidade de download e upload da sua internet e veja o que ela aguenta: vídeo, 4K, chamadas e jogos.',
    path: '/teste-de-download-e-upload',
  },
  '/teste-de-dns': {
    title: 'Teste de DNS: qual DNS usar? | SignallQ',
    description: 'Compare o tempo de resposta de DNS populares e aprenda a trocar o DNS no Android para abrir páginas mais rápido.',
    path: '/teste-de-dns',
  },
  '/teste-de-jitter': {
    title: 'Teste de jitter e perda de pacotes | SignallQ',
    description: 'Meça jitter e perda de pacotes e entenda por que chamadas e jogos travam mesmo com internet rápida.',
    path: '/teste-de-jitter',
  },
  '/canais-wifi': {
    title: 'Melhor canal de Wi-Fi: como escolher | SignallQ',
    description: 'Veja como saber se o canal do seu Wi-Fi está congestionado e qual canal usar. O app mostra a ocupação dos canais ao seu redor.',
    path: '/canais-wifi',
  },
  '/relatorio-pdf': {
    title: 'Relatório da sua internet em PDF para a operadora | SignallQ',
    description: 'Gere um relatório de diagnóstico em PDF e exporte o histórico de medições em CSV ou PDF para abrir um chamado com a operadora.',
    path: '/relatorio-pdf',
  },
  '/sinal-wifi': {
    title: 'Intensidade do sinal Wi-Fi: o que significa dBm | SignallQ',
    description: 'Entenda a intensidade do sinal Wi-Fi em dBm, o que é bom ou fraco e como melhorar o sinal em cada cômodo.',
    path: '/sinal-wifi',
  },
  '/quem-esta-na-minha-rede': {
    title: 'Quem está na minha rede Wi-Fi? | SignallQ',
    description: 'Descubra quais aparelhos estão conectados ao seu Wi-Fi, como identificar desconhecidos e o que fazer.',
    path: '/quem-esta-na-minha-rede',
  },
  '/mapa-de-calor-wifi': {
    title: 'Mapa de calor do Wi-Fi: sinal em cada cômodo | SignallQ',
    description: 'Veja como um mapa de calor mostra onde o Wi-Fi da sua casa é forte ou fraco e onde colocar o roteador.',
    path: '/mapa-de-calor-wifi',
  },
  '/sinal-4g-5g': {
    title: 'Sinal 4G e 5G: como saber se está bom | SignallQ',
    description: 'Veja o que seu navegador informa sobre a rede e entenda os níveis de sinal 4G e 5G em dBm.',
    path: '/sinal-4g-5g',
  },
  '/guias/por-que-minha-internet-esta-lenta': {
    title: 'Por que minha internet está lenta? Causas e o que fazer | SignallQ',
    description: 'Wi-Fi, roteador, operadora ou DNS: aprenda a descobrir a causa da internet lenta e como resolver.',
    path: '/guias/por-que-minha-internet-esta-lenta',
  },
  '/guias/como-melhorar-sinal-wifi': {
    title: 'Como melhorar o sinal do Wi-Fi em casa | SignallQ',
    description: 'Posição do roteador, banda 2,4 e 5 GHz, canal e repetidor: ajustes que melhoram o Wi-Fi.',
    path: '/guias/como-melhorar-sinal-wifi',
  },
  '/guias/latencia-jitter-dns': {
    title: 'O que são latência, jitter e DNS | SignallQ',
    description: 'Entenda o que significam ping, jitter, perda de pacotes e DNS e por que importam além da velocidade.',
    path: '/guias/latencia-jitter-dns',
  },
  '/guias/qual-velocidade-de-internet-preciso': {
    title: 'Qual velocidade de internet eu preciso? | SignallQ',
    description: 'Referência de Mbps para vídeo, 4K, videochamada e jogos, e como somar os aparelhos da casa.',
    path: '/guias/qual-velocidade-de-internet-preciso',
  },
}

export const NOT_FOUND_META: PageMeta = {
  title: 'Página não encontrada — SignallQ',
  description: 'A página que você acessou não existe ou foi movida.',
  path: '/404',
  robots: 'noindex,follow',
}
