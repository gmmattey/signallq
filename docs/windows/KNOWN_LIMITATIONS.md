# Limitações conhecidas — Windows Desktop

- A fidelidade 1:1 só pode ser atestada após screenshot comparado no Windows em 100%, 125% e 150%.
- O host macOS não prova WebView2, DPI, tray, permissões, instalador ou comportamento Windows 10/11.
- SSID, RSSI, canal, tabela de dispositivos e gateway dependem de APIs/serviços disponíveis. A UI
  deve mostrar indisponibilidade por adaptador, em especial Ethernet.
- Assist e avaliação NDS não têm ainda autenticação segura para cliente desktop distribuído; não
  recebem token embutido nem resposta substituta.
- A sonda gamer mede rota de referência, não servidor oficial de uma partida.
- Perda de pacotes baseada em sondagem HTTP é uma estimativa e deve conservar a proveniência.
- O inventário do adaptador está desabilitado de propósito até a integração tipada com APIs Windows:
  o parser textual de `ipconfig`/`netsh` poderia misturar VPNs e interfaces diferentes.
- O cancelamento cooperativo do speed test e a persistência de monitoramento após minimizar para a
  bandeja ainda não foram implementados; a UI não deve apresentar esses comportamentos como ativos.
