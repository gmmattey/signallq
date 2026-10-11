import { type GameGenre } from "./modoGamerEngine";

export interface GameEntry {
  id: string
  name: string
  genre: GameGenre
  img: string
}

export const GAMES_DATA: GameEntry[] = [
  { id: 'fortnite', name: 'Fortnite', genre: 'Battle royale', img: '/assets/games/fortnite.jpg' },
  { id: 'freefire', name: 'Free Fire', genre: 'Battle royale', img: '/assets/games/freefire.jpg' },
  { id: 'codm', name: 'COD Mobile', genre: 'FPS', img: '/assets/games/codm.jpg' },
  { id: 'valorant', name: 'Valorant', genre: 'FPS', img: '/assets/games/valorant.jpg' },
  { id: 'cs2', name: 'Counter-Strike 2', genre: 'FPS', img: '/assets/games/cs2.jpg' },
  { id: 'pubgm', name: 'PUBG Mobile', genre: 'Battle royale', img: '/assets/games/pubg.jpg' },
  { id: 'apex', name: 'Apex Legends', genre: 'Battle royale', img: '/assets/games/apexlegends.jpg' },
  { id: 'warzone', name: 'Warzone', genre: 'Battle royale', img: '/assets/games/warzone.jpg' },
  { id: 'lol', name: 'League of Legends', genre: 'MOBA', img: '/assets/games/leagueoflegends.jpg' },
  { id: 'wildrift', name: 'Wild Rift', genre: 'MOBA', img: '/assets/games/wildrift.jpg' },
  { id: 'dota2', name: 'Dota 2', genre: 'MOBA', img: '/assets/games/dota2.jpg' },
  { id: 'rocket', name: 'Rocket League', genre: 'Esportes', img: '/assets/games/rocketleague.jpg' },
  { id: 'fc', name: 'EA Sports FC', genre: 'Esportes', img: '/assets/games/eafc.jpg' },
  { id: 'rainbowsix', name: 'Rainbow Six', genre: 'FPS', img: '/assets/games/rainbowsix.jpg' },
  { id: 'bloodstrike', name: 'Blood Strike', genre: 'FPS', img: '/assets/games/bloodstrike.jpg' },
  { id: 'overwatch', name: 'Overwatch 2', genre: 'FPS', img: '/assets/games/overwatch.webp' },
  { id: 'brawl', name: 'Brawl Stars', genre: 'Casual', img: '/assets/games/brawlstars.jpg' },
  { id: 'roblox', name: 'Roblox', genre: 'Casual', img: '/assets/games/roblox.jpg' },
  { id: 'minecraft', name: 'Minecraft', genre: 'Casual', img: '/assets/games/minecraft.jpg' },
  { id: 'palworld', name: 'Palworld', genre: 'Casual', img: '/assets/games/palworld.jpg' },
  { id: 'rust', name: 'Rust', genre: 'FPS', img: '/assets/games/rust.jpg' },
  { id: 'dead-by-daylight', name: 'Dead by Daylight', genre: 'Casual', img: '/assets/games/dead-by-daylight.png' },
  { id: 'borderlands-3', name: 'Borderlands 3', genre: 'FPS', img: '/assets/games/borderlands-3.png' },
  { id: 'for-honor', name: 'For Honor', genre: 'Casual', img: '/assets/games/for-honor.png' },
  { id: 'new-world', name: 'New World', genre: 'Casual', img: '/assets/games/new-world.png' },
  { id: 'custom', name: 'Meu jogo não está na lista', genre: 'Casual', img: '/assets/games/fortnite.jpg' },
];

export const DEVICES = ['PlayStation', 'Xbox', 'PC', 'Android', 'iPhone', 'Switch', 'TV / cloud'];
export const GENRES = ['Todos', 'Battle royale', 'FPS', 'MOBA', 'Esportes', 'Casual'];

export const POPULAR_GAMES = [
  'Valorant',
  'Counter-Strike 2',
  'EA Sports FC',
  'Fortnite',
  'League of Legends',
  'Roblox',
  'Free Fire',
  'Warzone',
];

export const MODO_GAMER_COMMERCIAL_FAQ = [
  {
    q: 'O SignallQ mostra o ping exato do servidor do meu jogo?',
    a: 'Não. O aplicativo utiliza sondas em rotas de referência para estimar a estabilidade da conexão segundo os requisitos do jogo selecionado. Nenhum app externo mede o servidor privado de cada partida sem estar integrado ao próprio jogo.',
  },
  {
    q: 'O Modo Gamer melhora ou acelera a internet?',
    a: 'Não. O SignallQ é uma ferramenta de diagnóstico e orientação, não um acelerador de conexão ou serviço de VPN. Ele identifica onde está a oscilação para que você saiba o que ajustar.',
  },
  {
    q: 'O que o aplicativo Android analisa a mais que o teste no navegador?',
    a: 'No Android, o SignallQ tem acesso direto a métricas nativas do sistema: perda física de pacotes via UDP, intensidade de sinal Wi-Fi (dBm), congestionamento de canais vizinhos e parâmetros de rádio móvel.',
  },
  {
    q: 'Posso usar o aplicativo para avaliar conexões de PC ou console?',
    a: 'Sim. Ao conectar o celular na mesma rede Wi-Fi do seu console ou computador, o aplicativo diagnostica a estabilidade do link de internet que alimenta seus aparelhos.',
  },
  {
    q: 'Meu jogo não está no catálogo. Ainda posso avaliar?',
    a: 'Sim. Você pode selecionar a categoria equivalente (como FPS, MOBA, Battle Royale, Esportes ou Casual) e o aplicativo calibrará a avaliação para a sensibilidade desse gênero.',
  },
];

export const FAQ = MODO_GAMER_COMMERCIAL_FAQ;

