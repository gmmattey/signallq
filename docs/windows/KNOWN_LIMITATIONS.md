# Limitações conhecidas — Windows Desktop

Atualizado em 2026-10-15.

- O Desktop usa somente uma rota regional pública de referência para o Gamer (GameLift,
  São Paulo). Selecionar um jogo do catálogo Android não transforma essa rota em servidor do jogo.
  Outras regiões e endpoints específicos dependem de destinos validados.
- A coleta Wi-Fi depende do serviço WLAN e do driver. Canal e inventário de equipamentos não são
  coletados. Sem rota conhecida para o destino, a interface é mostrada com ressalva e a identidade
  da rede não é vinculada à medição.
- Identificador Wi-Fi usa BSSID ou SSID; redes com o mesmo SSID e sem BSSID acessível podem ser
  confundidas. Ethernet usa adaptador e gateway, sem provar identidade física de equipamentos.
- Perda HTTP é estimativa de sondas, não perda ICMP. Bufferbloat permanece indisponível porque não
  há sonda sob carga validada. Bloqueio HTTP, timeout e amostra insuficiente são distintos.
- Monitoramento mede disponibilidade/latência HTTP e DNS, sem download/upload. Três sondas a cada
  15 minutos não provam estabilidade contínua. Fechar o aplicativo interrompe a tarefa; a opção
  habilitada reinicia na próxima abertura.
- CSV pode conter identificadores locais após opção explícita. A exportação só ocorre após ação e escolha
  do caminho pelo usuário; não existe sincronização. Registros permanecem até exclusão explícita.
- Assist/NDS/IA não integram esta versão Desktop. A interpretação é priorizada no app mobile.
- O executável instalado por MSI passou por smoke em uma rede Ethernet neste Windows, inclusive
  medição, monitor, ferramentas e cancelamento. Exportação CSV e bandeja foram exercitadas no
  executável release de mesmo código antes da instalação. Segunda rede, Windows 10/11,
  VPN/múltiplos adaptadores, trocas reais de rede, DPI 100/125/150%, atualização e desinstalação
  ainda requerem evidência antes de distribuição geral.
