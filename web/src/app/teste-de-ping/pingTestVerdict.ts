export interface PingVerdict {
  status: 'Ótimo' | 'Bom' | 'Regular' | 'Ruim'
  color: string
  title: string
  subtitle: string
}

export interface PingUsageLevel {
  n: string
  s: 'Ótimo' | 'Bom' | 'Ruim'
  c: string
}

export function getPingVerdict(pingMin: number | null): PingVerdict {
  if (pingMin === null || pingMin <= 0) {
    return {
      status: 'Regular',
      color: '#B06000',
      title: 'Latência não determinada',
      subtitle: 'Não foi possível coletar amostras suficientes para classificar seu tempo de resposta.',
    }
  }

  if (pingMin < 30) {
    return {
      status: 'Ótimo',
      color: '#146C2E',
      title: 'Tempo de resposta excelente',
      subtitle: `Sua latência de ${pingMin} ms é instantânea. Sem atraso perceptível para jogos competitivos, reuniões ou navegação.`,
    }
  }

  if (pingMin < 60) {
    return {
      status: 'Bom',
      color: '#1E8E3E',
      title: 'Tempo de resposta estável e rápido',
      subtitle: `Com ${pingMin} ms, sua conexão responde com agilidade para praticamente qualquer atividade online.`,
    }
  }

  if (pingMin < 100) {
    return {
      status: 'Regular',
      color: '#B06000',
      title: 'Latência moderada',
      subtitle: `Com ${pingMin} ms, a navegação funciona bem, mas jogos de reflexo rápido e chamadas podem notar pequenos atrasos.`,
    }
  }

  return {
    status: 'Ruim',
    color: '#BA1A1A',
    title: 'Latência alta detectada',
    subtitle: `Seu ping de ${pingMin} ms pode causar travamentos perceptíveis em jogos, atrasos de áudio em chamadas e demora para abrir sites.`,
  }
}

export function getJitterStatus(jitter: number | null): { label: string; color: string } {
  if (jitter === null) return { label: '—', color: '#79747E' }
  if (jitter < 5) return { label: 'Excelente', color: '#146C2E' }
  if (jitter < 15) return { label: 'Bom', color: '#1E8E3E' }
  if (jitter < 30) return { label: 'Regular', color: '#B06000' }
  return { label: 'Instável', color: '#BA1A1A' }
}

export function getBufferbloatStatus(delta: number | null): { label: string; color: string } {
  if (delta === null) return { label: '—', color: '#79747E' }
  if (delta < 10) return { label: 'Excelente', color: '#146C2E' }
  if (delta < 30) return { label: 'Bom', color: '#1E8E3E' }
  if (delta < 80) return { label: 'Moderado', color: '#B06000' }
  return { label: 'Alto', color: '#BA1A1A' }
}

export function getPingUsageLevels(ping: number | null, jitter: number | null): PingUsageLevel[] {
  const p = ping ?? 999
  const j = jitter ?? 999

  const ok = '#146C2E'
  const good = '#1E8E3E'
  const bad = '#BA1A1A'

  // 1. Jogos competitivos
  let gameStatus: 'Ótimo' | 'Bom' | 'Ruim' = 'Ruim'
  let gameColor = bad
  if (p < 40 && j < 12) {
    gameStatus = 'Ótimo'
    gameColor = ok
  } else if (p < 75 && j < 25) {
    gameStatus = 'Bom'
    gameColor = good
  }

  // 2. Chamadas de vídeo e voz
  let callStatus: 'Ótimo' | 'Bom' | 'Ruim' = 'Ruim'
  let callColor = bad
  if (p < 80 && j < 25) {
    callStatus = 'Ótimo'
    callColor = ok
  } else if (p < 150) {
    callStatus = 'Bom'
    callColor = good
  }

  // 3. Navegação e streaming
  let webStatus: 'Ótimo' | 'Bom' | 'Ruim' = 'Ruim'
  let webColor = bad
  if (p < 120) {
    webStatus = 'Ótimo'
    webColor = ok
  } else if (p < 250) {
    webStatus = 'Bom'
    webColor = good
  }

  return [
    { n: 'Jogos online competitivos (FPS, MOBA)', s: gameStatus, c: gameColor },
    { n: 'Chamadas de voz e vídeo (Google Meet, WhatsApp)', s: callStatus, c: callColor },
    { n: 'Navegação em sites e streaming de vídeo', s: webStatus, c: webColor },
  ]
}
