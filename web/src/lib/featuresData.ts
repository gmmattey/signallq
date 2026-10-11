export interface FeatureRow {
  a: string;
  b: string;
}

export interface FeatureSection {
  h: string;
  ps: string[];
}

export interface FeatureSample {
  t: string;
  rows: [string, string, string][];
}

export interface FeatureData {
  slug: string;
  url: string;
  tool: 'app' | 'ping' | 'speed' | 'dns' | 'jitter' | 'mobile';
  star?: boolean;
  short: string;
  nav: string;
  title: string;
  desc: string;
  eyebrow: string;
  h1: string;
  intro: string;
  rt: string;
  rows: FeatureRow[];
  sample?: FeatureSample;
  secs: FeatureSection[];
  faq: [string, string][];
  app: string;
}

export const FEATURES: FeatureData[] = [
  {
    slug: 'ping',
    url: '/teste-de-ping',
    tool: 'ping',
    short: 'Quanto tempo sua conexão leva para responder.',
    nav: 'Teste de ping',
    title: 'Teste de ping e latência da internet | SignallQ',
    desc: 'Meça o ping da sua internet em segundos e entenda se a latência é boa para jogos, chamadas e navegação.',
    eyebrow: 'Ping e latência',
    h1: 'Qual é o ping da sua internet?',
    intro:
      'Ping é o tempo, em milissegundos, que uma mensagem leva para ir até um servidor e voltar. Quanto menor, mais rápida a resposta em jogos, chamadas e navegação.',
    rt: 'Como ler o resultado',
    rows: [
      { a: 'Até 30 ms', b: 'Excelente. Sem atraso perceptível.' },
      { a: '30 a 60 ms', b: 'Bom. Serve para quase tudo, inclusive jogos.' },
      { a: '60 a 100 ms', b: 'Razoável. Jogos rápidos começam a sentir.' },
      { a: 'Acima de 100 ms', b: 'Alto. Atraso visível em chamadas e jogos.' },
    ],
    secs: [
      {
        h: 'O que é ping',
        ps: [
          'É a latência de ida e volta até um servidor. Não mede quantos dados passam por segundo, mede quanto tempo a resposta demora.',
          'Por isso uma conexão de 500 Mbps pode ter ping alto e ainda assim parecer lenta ao abrir páginas e jogar.',
        ],
      },
      {
        h: 'O que faz o ping subir',
        ps: [
          'Wi-Fi fraco ou com interferência, rede congestionada, distância do servidor, VPN ligada e roteador sobrecarregado.',
        ],
      },
      {
        h: 'Como baixar o ping',
        ps: [
          'Use cabo quando possível, aproxime-se do roteador, pause downloads e atualizações em andamento e reinicie o roteador. Se continuar alto nos dois lugares, o problema pode estar na operadora.',
        ],
      },
    ],
    faq: [
      [
        'Qual ping é bom para jogar online?',
        'Até 50 ms costuma ser ótimo. Entre 50 e 100 ms ainda é jogável; acima disso, jogos competitivos ficam prejudicados.',
      ],
      ['Ping e velocidade são a mesma coisa?', 'Não. Velocidade é quantidade de dados por segundo. Ping é o tempo de resposta.'],
      ['Por que meu ping muda a cada teste?', 'A latência varia com o momento, o Wi-Fi e o tráfego na rede. Por isso o teste faz várias medições.'],
    ],
    app: 'No app, a latência é medida junto com Wi-Fi, DNS e rede móvel, para dizer qual deles causa o atraso.',
  },
  {
    slug: 'velocidade',
    url: '/teste-de-download-e-upload',
    tool: 'speed',
    short: 'Quantos Mbps chegam para baixar e enviar.',
    nav: 'Download e upload',
    title: 'Teste de download e upload | SignallQ',
    desc: 'Meça a velocidade de download e upload da sua internet e veja o que ela aguenta: vídeo, 4K, chamadas e jogos.',
    eyebrow: 'Download e upload',
    h1: 'Quantos Mbps sua internet entrega?',
    intro:
      'Download é a velocidade para receber dados: vídeos, páginas, arquivos. Upload é a velocidade para enviar: fotos, backups e videochamadas.',
    rt: 'Referência por uso',
    rows: [
      { a: '3 a 5 Mbps', b: 'Mensagens, redes sociais e e-mail.' },
      { a: '5 a 8 Mbps', b: 'Vídeo em HD, por tela.' },
      { a: 'Cerca de 25 Mbps', b: 'Vídeo em 4K, por tela.' },
      { a: '3 a 4 Mbps de upload', b: 'Videochamada estável.' },
    ],
    secs: [
      {
        h: 'Download e upload não são iguais',
        ps: [
          'Na maioria dos planos residenciais o download é bem maior que o upload. Para assistir e navegar, o download pesa mais; para chamadas, nuvem e envio de vídeo, o upload.',
        ],
      },
      {
        h: 'Por que o resultado muda',
        ps: [
          'O teste mostra a velocidade naquele momento, naquele lugar. Wi-Fi distante do roteador, horário de pico e outros aparelhos usando a rede reduzem o número.',
          'Para comparar com o plano, teste perto do roteador, de preferência com cabo.',
        ],
      },
      {
        h: 'Contratado não é entregue',
        ps: ['A velocidade do plano é o máximo da operadora. No Wi-Fi, o que chega ao seu aparelho costuma ser menor.'],
      },
    ],
    faq: [
      [
        'Por que meu download está abaixo do plano?',
        'Wi-Fi, horário, aparelhos conectados e limite do próprio aparelho reduzem o resultado. Teste perto do roteador para separar causas.',
      ],
      ['Preciso de mais upload?', 'Se você faz videochamadas, envia vídeos ou usa backup na nuvem, vale conferir. Para o resto, o download basta.'],
      ['Este teste gasta muitos dados?', 'Ele baixa e envia alguns megabytes. Em rede móvel com franquia, prefira Wi-Fi.'],
    ],
    app: 'No app, o teste de velocidade é só uma parte: ele também explica se o problema está no Wi-Fi, no roteador ou na operadora.',
  },
  {
    slug: 'dns',
    url: '/teste-de-dns',
    tool: 'dns',
    short: 'Qual DNS responde mais rápido para você.',
    nav: 'Teste de DNS',
    title: 'Teste de DNS: qual DNS usar? | SignallQ',
    desc: 'Compare o tempo de resposta de DNS populares e aprenda a trocar o DNS no Android para abrir páginas mais rápido.',
    eyebrow: 'DNS',
    h1: 'Qual DNS responde mais rápido para você?',
    intro:
      'DNS é a “lista telefônica” da internet: traduz o nome de um site no endereço que o aparelho entende. Um DNS lento atrasa o início de toda página.',
    rt: 'Tempo de resposta do DNS',
    rows: [
      { a: 'Até 20 ms', b: 'Ótimo.' },
      { a: '20 a 50 ms', b: 'Bom.' },
      { a: '50 a 100 ms', b: 'Lento. Páginas demoram a começar.' },
      { a: 'Acima de 100 ms', b: 'Muito lento. Vale trocar de DNS.' },
    ],
    secs: [
      {
        h: 'O que este teste mede',
        ps: [
          'Ele mede o tempo de resposta, a partir do seu navegador, de três serviços públicos: Cloudflare (1.1.1.1), Google (8.8.8.8) e Quad9 (9.9.9.9). Não mostra qual DNS sua rede usa hoje, mas indica qual responde melhor aí.',
        ],
      },
      {
        h: 'Como trocar o DNS no Android',
        ps: [
          'Abra Configurações, procure por DNS privado e escolha “Nome do host do provedor de DNS privado”. Digite one.one.one.one (Cloudflare), dns.google (Google) ou dns.quad9.net (Quad9).',
          'Para voltar ao normal, escolha “Automático”.',
        ],
      },
      {
        h: 'Quando trocar de DNS ajuda sem mudar a velocidade',
        ps: [
          'Se as páginas demoram a abrir mas a velocidade está boa, o DNS costuma ser a causa. Trocar não aumenta os Mbps, só reduz a espera inicial.',
        ],
      },
    ],
    faq: [
      ['Qual é o melhor DNS?', 'Depende de onde você está. Por isso o teste compara os três a partir da sua conexão.'],
      ['Trocar o DNS é seguro?', 'Sim, são serviços públicos conhecidos. A troca é reversível a qualquer momento.'],
      ['Trocar o DNS aumenta a velocidade?', 'Não aumenta os Mbps. Reduz o tempo para as páginas começarem a carregar.'],
    ],
    app: 'No app, o DNS da sua rede é testado direto, e você só vê o aviso se ele for a causa do problema.',
  },
  {
    slug: 'jitter',
    url: '/teste-de-jitter',
    tool: 'jitter',
    short: 'Oscilação da latência e perda de pacotes.',
    nav: 'Jitter e perda de pacotes',
    title: 'Teste de jitter e perda de pacotes | SignallQ',
    desc: 'Meça jitter e perda de pacotes e entenda por que chamadas e jogos travam mesmo com internet rápida.',
    eyebrow: 'Jitter e perda de pacotes',
    h1: 'Por que a chamada engasga com internet rápida?',
    intro:
      'Jitter é a variação do ping. Perda de pacotes é quando parte dos dados some no caminho. Os dois deixam chamadas e jogos instáveis, mesmo com muitos Mbps.',
    rt: 'Como ler o resultado',
    rows: [
      { a: 'Jitter até 10 ms', b: 'Ótimo. Chamadas e jogos estáveis.' },
      { a: 'Jitter de 10 a 30 ms', b: 'Aceitável. Pode oscilar.' },
      { a: 'Jitter acima de 30 ms', b: 'Ruim. Áudio picotado e saltos no jogo.' },
      { a: 'Perda de 0%', b: 'Ideal.' },
      { a: 'Perda acima de 2%', b: 'Ruim. Travadas e imagem congelada.' },
    ],
    secs: [
      {
        h: 'Jitter',
        ps: ['Se o ping oscila entre 20 e 120 ms, o jitter é alto. A média pode parecer boa, mas a experiência é ruim.'],
      },
      {
        h: 'Perda de pacotes',
        ps: ['Pacotes perdidos precisam ser reenviados, o que causa travadas, áudio cortado e imagem congelada.'],
      },
      {
        h: 'Como reduzir',
        ps: [
          'Prefira cabo ou 5 GHz, aproxime-se do roteador, evite downloads durante chamadas e reinicie o roteador. Se persistir, anote os valores e fale com a operadora.',
        ],
      },
    ],
    faq: [
      ['Qual jitter é aceitável para jogos?', 'Até 10 ms é ótimo. Acima de 30 ms você começa a perceber saltos.'],
      ['Perda de pacotes tem solução?', 'Se vem do Wi-Fi, melhora com cabo ou sinal melhor. Se vem da operadora, só ela resolve.'],
      ['Este teste é exato?', 'É uma medição rápida pelo navegador. O app faz uma análise mais completa.'],
    ],
    app: 'No app, jitter e perda de pacotes entram no diagnóstico e aparecem só quando explicam o problema.',
  },
  {
    slug: 'canais',
    url: '/canais-wifi',
    tool: 'app',
    star: true,
    short: 'Descubra o canal mais livre para o seu roteador.',
    nav: 'Canais do Wi-Fi',
    title: 'Melhor canal de Wi-Fi: como escolher | SignallQ',
    desc: 'Veja como saber se o canal do seu Wi-Fi está congestionado e qual canal usar. O app mostra a ocupação dos canais ao seu redor.',
    eyebrow: 'Canais do Wi-Fi · só no app',
    h1: 'Qual é o melhor canal de Wi-Fi para o seu roteador?',
    intro:
      'Em prédios e ruas movimentadas, várias redes disputam o mesmo canal e todas ficam mais lentas. A aba Canal do app mostra quais canais estão livres e recomenda um.',
    rt: 'Ocupação do canal',
    rows: [
      { a: 'Livre', b: 'Poucas redes por perto. Não precisa mudar.' },
      { a: 'Moderado', b: 'Há concorrência. Pode oscilar no horário de pico.' },
      { a: 'Congestionado', b: 'Muitas redes no canal. Vale trocar.' },
    ],
    sample: {
      t: 'Exemplo do que o app mostra',
      rows: [
        ['Seu canal atual', 'Canal 6 · Congestionado', '#BA1A1A'],
        ['Canal recomendado', 'Canal 11 · Livre', '#146C2E'],
        ['Redes no seu canal', '7', ''],
        ['Banda', '2,4 GHz', ''],
      ],
    },
    secs: [
      {
        h: 'Por que o canal importa',
        ps: [
          'Redes no mesmo canal dividem o tempo de transmissão. Quanto mais redes, mais espera. Na banda de 2,4 GHz, os canais 1, 6 e 11 não se sobrepõem, por isso são os mais usados.',
        ],
      },
      {
        h: 'O que a aba Canal mostra',
        ps: [
          'Uma lista de ocupação dos canais, ordenada pelo congestionamento, com status Livre, Moderado ou Congestionado. Mostra um aviso por vez: canal congestionado, canal limpo ou canal recomendado.',
          'Se você está em 2,4 GHz e a mesma rede existe em 5 GHz, o app avisa que vale trocar de banda.',
        ],
      },
      {
        h: 'Como trocar de canal',
        ps: [
          'A troca é feita no painel do roteador, em configurações de Wi-Fi. O app não altera nada na rede: ele mede e orienta. Depois da troca, a tela atualiza sozinha e você vê o efeito.',
        ],
      },
    ],
    faq: [
      [
        'Por que o app pede localização?',
        'O Android exige essa permissão para liberar a lista de redes vizinhas. O app não rastreia sua localização por GPS.',
      ],
      ['Posso ver os canais pelo navegador?', 'Não. O navegador não enxerga as redes ao redor. Só o app Android faz essa varredura.'],
      ['Trocar de canal resolve tudo?', 'Ajuda quando a causa é interferência. Se o sinal está fraco, mudar o roteador de lugar costuma ser mais efetivo.'],
    ],
    app: 'A análise de canais está em Ferramentas, em “Wi-Fi e rede móvel”, no app Android.',
  },
  {
    slug: 'relatorio',
    url: '/relatorio-pdf',
    tool: 'app',
    star: true,
    short: 'Relatório de diagnóstico e histórico para levar à operadora.',
    nav: 'Relatório em PDF',
    title: 'Relatório da sua internet em PDF para a operadora | SignallQ',
    desc: 'Gere um relatório de diagnóstico em PDF e exporte o histórico de medições em CSV ou PDF para abrir um chamado com a operadora.',
    eyebrow: 'Relatório e exportação · só no app',
    h1: 'Leve o diagnóstico da sua internet em PDF.',
    intro:
      'Para falar com a operadora, números soltos não bastam. O SignallQ gera um relatório de diagnóstico em PDF e exporta o histórico de medições em CSV ou PDF.',
    rt: 'O que o relatório traz',
    rows: [
      { a: 'Resumo e score', b: 'Um banner de status com a conclusão em palavras.' },
      { a: 'Seis métricas', b: 'Download, upload, latência, jitter, perda e bufferbloat.' },
      { a: 'Recomendação', b: 'O que fazer a seguir, em linguagem simples.' },
      { a: 'Privacidade', b: 'Nome omitido. Rede e endereços IP mascarados.' },
    ],
    sample: {
      t: 'Exemplo do que o app gera',
      rows: [
        ['Relatório de diagnóstico', 'PDF · A4', ''],
        ['Métricas incluídas', '6', ''],
        ['Nome, rede e IP', 'Omitidos / mascarados', '#146C2E'],
        ['Histórico', 'CSV ou PDF', ''],
      ],
    },
    secs: [
      {
        h: 'Relatório de diagnóstico',
        ps: [
          'Abra o relatório em Ferramentas, em “Relatório para sua operadora”. Ele reúne a conclusão do diagnóstico e as seis métricas principais, e pode ser compartilhado em PDF.',
        ],
      },
      {
        h: 'Exportar o histórico',
        ps: [
          'No Histórico, o ícone de exportação abre as opções: período de 7 dias, 30 dias ou tudo, e formato CSV ou PDF. A exportação respeita o filtro que você aplicou.',
          'O CSV traz data, hora, download, upload, latência, jitter, perda, bufferbloat e fonte.',
        ],
      },
      {
        h: 'Como usar com a operadora',
        ps: [
          'Anexe o PDF ao chamado, cite os dias e horários das medições e peça o número de protocolo. Com evidência de vários momentos, o atendimento localiza o problema mais rápido.',
        ],
      },
    ],
    faq: [
      ['O PDF mostra meus dados pessoais?', 'Não. O nome do usuário é omitido e o nome da rede e os endereços IP aparecem mascarados.'],
      ['Posso exportar só os últimos dias?', 'Sim. Escolha 7 dias, 30 dias ou tudo.'],
      ['A operadora é avisada automaticamente?', 'Não. O app não envia nada à operadora. Você decide se compartilha o arquivo.'],
    ],
    app: 'O relatório e a exportação do histórico são recursos do app Android.',
  },
  {
    slug: 'wifi',
    url: '/sinal-wifi',
    tool: 'app',
    short: 'O que o dBm do sinal significa de verdade.',
    nav: 'Sinal Wi-Fi',
    title: 'Intensidade do sinal Wi-Fi: o que significa dBm | SignallQ',
    desc: 'Entenda a intensidade do sinal Wi-Fi em dBm, o que é bom ou fraco e como melhorar o sinal em cada cômodo.',
    eyebrow: 'Sinal Wi-Fi',
    h1: 'Seu sinal de Wi-Fi está bom ou fraco?',
    intro:
      'A intensidade do sinal é medida em dBm, sempre em valor negativo. Quanto mais perto de zero, mais forte. O navegador não consegue ler esse valor; o app Android lê.',
    rt: 'Intensidade do sinal (dBm)',
    rows: [
      { a: '−30 a −50', b: 'Excelente. Perto do roteador.' },
      { a: '−50 a −60', b: 'Muito bom. Serve para tudo.' },
      { a: '−60 a −70', b: 'Bom. Vídeo e navegação sem problema.' },
      { a: '−70 a −80', b: 'Fraco. Quedas e lentidão.' },
      { a: 'Abaixo de −80', b: 'Instável. Quase sem conexão.' },
    ],
    sample: {
      t: 'Exemplo do que o app mostra',
      rows: [
        ['Sinal no cômodo', '−58 dBm · Bom', '#146C2E'],
        ['Banda', '5 GHz', ''],
        ['Canal', '36', ''],
        ['Vizinhos no mesmo canal', '3', '#8A5000'],
      ],
    },
    secs: [
      {
        h: 'Sinal forte não é velocidade alta',
        ps: [
          'O sinal mostra o quão bem o aparelho “escuta” o roteador. A velocidade ainda depende do plano, da banda e de quantos aparelhos usam a rede.',
        ],
      },
      {
        h: 'O que enfraquece o sinal',
        ps: ['Distância, paredes grossas, espelhos, aquários e eletrodomésticos como o micro-ondas.'],
      },
      {
        h: 'Como melhorar',
        ps: [
          'Coloque o roteador no centro da casa, em lugar alto e aberto. Use 5 GHz perto dele e 2,4 GHz nos cômodos distantes. Se não bastar, considere repetidor ou rede mesh.',
        ],
      },
    ],
    faq: [
      ['O que é dBm?', 'É a unidade de potência do sinal. No Wi-Fi o valor é negativo: −50 é mais forte que −70.'],
      ['Qual sinal é bom para vídeo?', 'Até −70 dBm costuma funcionar bem. Abaixo de −75 dBm aparecem travadas.'],
      ['Posso medir o sinal pelo navegador?', 'Não. Navegadores não leem o sinal do Wi-Fi. O app Android lê e mostra por cômodo.'],
    ],
    app: 'O app lê a intensidade, a banda e o canal do Wi-Fi e diz em palavras se o cômodo está bom.',
  },
  {
    slug: 'dispositivos',
    url: '/quem-esta-na-minha-rede',
    tool: 'app',
    short: 'Veja os aparelhos conectados ao seu Wi-Fi.',
    nav: 'Dispositivos na rede',
    title: 'Quem está na minha rede Wi-Fi? | SignallQ',
    desc: 'Descubra quais aparelhos estão conectados ao seu Wi-Fi, como identificar desconhecidos e o que fazer.',
    eyebrow: 'Dispositivos na rede',
    h1: 'Quem está conectado ao seu Wi-Fi?',
    intro:
      'Vários aparelhos usando a rede ao mesmo tempo deixam tudo mais lento. Ver a lista ajuda a achar o que consome mais e a notar um aparelho que você não reconhece.',
    rt: 'Sinais de aparelho desconhecido',
    rows: [
      { a: 'Nome genérico', b: 'Aparece só como “Android” ou um código.' },
      { a: 'Fabricante não identificado', b: 'O endereço MAC não bate com marcas conhecidas.' },
      { a: 'Conectado quando todos estão fora', b: 'Pode ser um aparelho esquecido ou intruso.' },
    ],
    sample: {
      t: 'Exemplo do que o app mostra',
      rows: [
        ['Pixel 8', 'Celular', ''],
        ['Smart TV da sala', 'TV', ''],
        ['Notebook', 'Computador', ''],
        ['Aparelho desconhecido', 'Verificar', '#8A5000'],
      ],
    },
    secs: [
      {
        h: 'O que o app mostra',
        ps: ['Nome, tipo e endereço MAC dos aparelhos na rede local. O app nunca vê o conteúdo do tráfego.'],
      },
      {
        h: 'Por que isso ajuda',
        ps: [
          'A lentidão pode vir de um aparelho baixando atualizações ou transmitindo vídeo. Identificar quem está na rede mostra onde agir.',
        ],
      },
      {
        h: 'Se encontrar um desconhecido',
        ps: [
          'Troque a senha do Wi-Fi no roteador, use criptografia WPA2 ou WPA3 e reconecte seus aparelhos. Desligue o WPS se não usar.',
        ],
      },
    ],
    faq: [
      ['Dá para ver quem está na rede pelo navegador?', 'Não. Sites não enxergam a rede local. O app Android faz essa varredura no seu aparelho.'],
      ['Alguém pode estar roubando meu Wi-Fi?', 'É possível, mas aparelhos esquecidos são mais comuns. Compare a lista com o que você tem em casa.'],
      ['O app vê o que cada aparelho acessa?', 'Não. Ele só identifica os aparelhos, nunca o conteúdo do tráfego.'],
    ],
    app: 'No app, a varredura roda no seu aparelho e a lista fica só nele.',
  },
  {
    slug: 'mapa',
    url: '/mapa-de-calor-wifi',
    tool: 'app',
    short: 'O sinal do Wi-Fi em cada cômodo da casa.',
    nav: 'Mapa de calor do Wi-Fi',
    title: 'Mapa de calor do Wi-Fi: sinal em cada cômodo | SignallQ',
    desc: 'Veja como um mapa de calor mostra onde o Wi-Fi da sua casa é forte ou fraco e onde colocar o roteador.',
    eyebrow: 'Mapa do Wi-Fi Casa',
    h1: 'Onde o Wi-Fi da sua casa cai?',
    intro:
      'Um mapa de calor mostra a força do sinal em cada cômodo. Em vez de adivinhar, você vê onde o sinal some e onde vale mover o roteador ou instalar um repetidor.',
    rt: 'Como ler as cores',
    rows: [
      { a: 'Verde', b: 'Sinal bom. Sem problema para vídeo e chamadas.' },
      { a: 'Amarelo', b: 'Sinal médio. Pode oscilar com mais aparelhos.' },
      { a: 'Vermelho', b: 'Sinal fraco. Quedas e lentidão.' },
    ],
    sample: {
      t: 'Exemplo do que o app mostra',
      rows: [
        ['Sala', 'Bom', '#146C2E'],
        ['Cozinha', 'Bom', '#146C2E'],
        ['Escritório', 'Médio', '#8A5000'],
        ['Quarto', 'Fraco', '#BA1A1A'],
      ],
    },
    secs: [
      {
        h: 'Como o mapa é feito',
        ps: [
          'No app Android, você anda pela casa e mede o sinal em cada cômodo. O resultado fica salvo no aparelho como o Mapa do Wi-Fi Casa.',
        ],
      },
      {
        h: 'O que fazer com o mapa',
        ps: [
          'Se o cômodo mais usado está fraco, mude o roteador de lugar antes de comprar equipamento. Se a casa tem vários pontos vermelhos, um repetidor ou rede mesh resolve.',
        ],
      },
      {
        h: 'Meça de novo depois',
        ps: ['Depois de mudar algo, repita a medição no mesmo cômodo para confirmar que melhorou.'],
      },
    ],
    faq: [
      ['Preciso de equipamento especial?', 'Não. O app usa o próprio celular para medir o sinal.'],
      ['Quantos cômodos devo medir?', 'Pelo menos onde você usa a internet: sala, quarto, escritório e cozinha.'],
      ['O mapa muda com o tempo?', 'Sim. Móveis, vizinhos e novos aparelhos mudam o sinal. Repita se algo mudar.'],
    ],
    app: 'O Mapa do Wi-Fi Casa é uma função do app Android.',
  },
  {
    slug: 'movel',
    url: '/sinal-4g-5g',
    tool: 'mobile',
    short: 'Qualidade do sinal da rede móvel.',
    nav: 'Sinal 4G e 5G',
    title: 'Sinal 4G e 5G: como saber se está bom | SignallQ',
    desc: 'Veja o que seu navegador informa sobre a rede e entenda os níveis de sinal 4G e 5G em dBm.',
    eyebrow: 'Rede móvel',
    h1: 'Seu sinal 4G ou 5G está bom?',
    intro:
      'Na rede móvel, a qualidade depende do sinal da torre e da tecnologia. O navegador informa só uma estimativa; o app Android lê o sinal real em dBm.',
    rt: 'Sinal 4G em dBm (referência)',
    rows: [
      { a: 'Melhor que −85', b: 'Excelente.' },
      { a: '−85 a −105', b: 'Bom.' },
      { a: '−105 a −115', b: 'Fraco. Velocidade cai.' },
      { a: 'Abaixo de −115', b: 'Muito fraco. Quedas frequentes.' },
    ],
    secs: [
      {
        h: 'O que o navegador consegue informar',
        ps: [
          'Em alguns navegadores Android, é possível ver o tipo efetivo de conexão, uma estimativa de velocidade e o tempo de resposta. Não aparece o nível em dBm nem a operadora.',
        ],
      },
      {
        h: 'O que piora o sinal',
        ps: ['Estar longe da torre, dentro de prédios, subsolos, carros e áreas com muitos usuários.'],
      },
      {
        h: 'O que tentar',
        ps: ['Vá para perto de uma janela, ative e desative o modo avião para reconectar e, se possível, use o Wi-Fi.'],
      },
    ],
    faq: [
      ['Por que o 5G às vezes é lento?', 'Sinal fraco ou torre congestionada limita a velocidade, mesmo com o ícone de 5G.'],
      ['O teste mostra o sinal em dBm?', 'Não. Só o app Android lê o dBm do celular.'],
      ['Vale usar Wi-Fi em vez do 4G?', 'Se o Wi-Fi tem sinal bom, quase sempre é mais estável e não gasta franquia.'],
    ],
    app: 'No app, o sinal móvel é lido direto do celular e comparado com Wi-Fi e DNS.',
  },
];

export function getFeatureBySlug(slug: string): FeatureData | undefined {
  return FEATURES.find((f) => f.slug === slug || f.url.replace('/', '') === slug.replace('/', ''));
}

export function getRelatedFeatures(slug: string): FeatureData[] {
  const current = getFeatureBySlug(slug);
  if (!current) return FEATURES.slice(0, 3);
  const idx = FEATURES.indexOf(current);
  return [1, 2, 3].map((n) => FEATURES[(idx + n) % FEATURES.length]);
}
