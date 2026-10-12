const COVER: Record<string, string> = {
  fortnite: 'fortnite.jpg',
  codm: 'codm.jpg',
  'call-of-duty-mobile': 'codm.jpg',
  freefire: 'freefire.jpg',
  'free-fire': 'freefire.jpg',
  pubgm: 'pubg.jpg',
  'pubg-mobile': 'pubg.jpg',
  brawl: 'brawlstars.jpg',
  'brawl-stars': 'brawlstars.jpg',
  wildrift: 'wildrift.jpg',
  'wild-rift': 'wildrift.jpg',
  roblox: 'roblox.jpg',
  minecraft: 'minecraft.jpg',
  apex: 'apexlegends.jpg',
  'apex-legends': 'apexlegends.jpg',
  bloodstrike: 'bloodstrike.jpg',
  'blood-strike': 'bloodstrike.jpg',
  cs2: 'cs2.jpg',
  dota2: 'dota2.jpg',
  fc: 'eafc.jpg',
  eafc: 'eafc.jpg',
  lol: 'leagueoflegends.jpg',
  'league-of-legends': 'leagueoflegends.jpg',
  palworld: 'palworld.jpg',
  rainbowsix: 'rainbowsix.jpg',
  'rainbow-six': 'rainbowsix.jpg',
  rocket: 'rocketleague.jpg',
  'rocket-league': 'rocketleague.jpg',
  rust: 'rust.jpg',
  valorant: 'valorant.jpg',
  warzone: 'warzone.jpg',
  overwatch: 'overwatch.webp',
  'overwatch-2': 'overwatch.webp',
  'dead-by-daylight': 'dead-by-daylight.png',
  'borderlands-3': 'borderlands-3.png',
  'for-honor': 'for-honor.png',
  'new-world': 'new-world.png',
}

export function coverOf(id: string): string | null {
  const key = id.toLowerCase().trim()
  const f = COVER[key] ?? COVER[key.replace(/\s+/g, '-')] ?? COVER[key.replace(/[^a-z0-9]/g, '')]
  return f ? `/assets/games/${f}` : null
}

