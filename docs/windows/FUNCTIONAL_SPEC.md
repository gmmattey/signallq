# Especificação funcional — Windows Desktop (fundação atual)

O desktop mantém a jornada **entender → diagnosticar → resolver → confirmar**. A referência visual
tem Visão geral, Velocidade, Diagnóstico, Modo gamer, Minha rede, Monitoramento, Histórico, Assist,
Ferramentas e Configurações.

- **Visão geral:** mostra o último resultado válido. O snapshot do adaptador está explicitamente
  indisponível até o adaptador Windows tipado ser validado.
- **Velocidade:** mede latência, download e upload; atraso sob carga (bufferbloat) é
  explicitamente indisponível até a coleta concorrente ser validada. Cancelamento cooperativo é
  pendência.
- **Diagnóstico:** usa evidências e limites explícitos. Não há conclusão causal sem suporte.
- **Gamer:** mede rota regional de referência; jamais promete ou rotula ping do jogo.
- **Minha rede e ferramentas:** mostram só dados disponíveis ao Windows; DNS, ping e rota possuem
  alvo validado, timeout e saída limitada.
- **Monitoramento e histórico:** histórico persiste medições no SQLite. O monitor só roda enquanto
  a janela está aberta; pausa/retomada persistente, filtros e exportação local são pendências.
- **Assist:** até autenticação desktop compatível, exibe indisponibilidade recuperável — sem
  simular resposta de IA.
- **Configurações:** nesta fundação, somente a preferência de tema persiste localmente. Retenção,
  demais preferências, inicialização e bandeja são pendências.

Estados implementados: primeira execução, sem histórico, medição em curso/com erro, resultado
parcial e capacidade não suportada. Cancelamento, retorno após reinício, Wi-Fi/Ethernet validado e
modo offline ainda são pendências. Ausência é sempre visualizada como indisponível, não como
métrica zero.
