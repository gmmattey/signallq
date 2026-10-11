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

export const FAQ = [
  {
    q: 'O app mostra o ping do meu servidor de jogo?',
    a: 'Não. Ele mede uma rota regional de referência e avalia a conexão para o tipo de jogo. Não mede o servidor do jogo, por isso nunca promete “o ping do seu jogo”.',
  },
  {
    q: 'Meu jogo não está na lista. E agora?',
    a: 'Escolha “Meu jogo não está na lista” e uma das 5 categorias genéricas. O veredito continua calibrado para as exigências desse gênero.',
  },
  {
    q: 'Preciso de Android para usar?',
    a: 'O app é para Android, mas você pode avaliar a conexão para jogar em PlayStation, Xbox, PC, Switch, iPhone ou TV.',
  },
];
