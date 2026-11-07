# SignallQ Desktop — comportamento implementado

Atualizado em 2026-10-15. O Desktop coleta e apresenta dados de conectividade. Interpretação
guiada, Assist e consulta com IA permanecem na experiência mobile. O código e os testes têm
precedência sobre este resumo; critérios E2E estão em `CORRECTION_PLAN_2026-10-15.md`.

| Tela | Comportamento |
|---|---|
| Visão geral | Última medição, estado de integridade e valores disponíveis; nenhuma classificação de qualidade por threshold local. |
| Velocidade | Oferece somente teste completo: janela de 18 s para download e 18 s para upload, além da latência, sem seleção de modo rápido. O limite é 2,5 GB por fase; se atingido antes da janela, a fase fica inconclusiva, sem Mbps válido. Inicia/cancela medição Tauri com eventos de progresso. Registra fases, método, endpoint, amostras, bytes, erros HTTP, fallback e valores opcionais em SQLite v2. Histórico v1 continua legível. |
| Dados da conexão | Agrupa fatos da medição e da interface; não conclui causa. |
| Modo gamer | Sonda UDP SQPB v1 na rota regional GameLift São Paulo, com amostras e estatísticas compatíveis com Android. O jogo de interesse vem de IDs do catálogo Android, mas não altera a rota: nenhum resultado é chamado de ping do servidor do jogo. |
| Minha rede | Usa IP Helper e WLAN do Windows para interface na rota escolhida, IP, gateway, DNS, tipo, SSID e sinal quando disponíveis. O canal fica indisponível; o identificador Wi-Fi segue BSSID > SSID, como no Android. |
| Monitoramento | Sondas HTTP/DNS leves a cada 15 minutos em tarefa Rust, registradas em tabela própria. Minimizar esconde a janela na bandeja e mantém a tarefa; fechar ou Sair encerra a aplicação. A preferência é local e reinicia o monitor na próxima abertura. |
| Histórico | Lista até 500 medições na UI; filtra pela rede identificada e compara as duas últimas completas dessa rede. Exporta todo o histórico de velocidade e monitor em CSV local mediante seleção do arquivo. SSID/IP/identificador coletados entram apenas após o usuário marcar essa opção na prévia. Exclui um registro ou todos após confirmação. |
| Ferramentas | Ping, DNS e rota aceitam host/IP validado. Processos têm prazo e são encerrados em timeout; consulta DNS vazia não conta como sucesso. Sequência gateway → DNS → rota externa mostra cada observação sem atribuir causa. |
| Configurações | Tema local; preferências do monitor ficam no banco. |

Ausência de métrica é `null`, exibida como “Indisponível”. `COMPLETE`, `PARTIAL`, `INCONCLUSIVE`,
`CONTAMINATED` e `CANCELLED` indicam integridade da coleta, não qualidade da conexão. O CSV é
UTF-8, versionado, com aspas e neutralização de fórmulas em campos de texto. Nenhum inventário,
SSID ou IP é enviado automaticamente a serviço remoto.

O build e os testes locais não comprovam o comportamento do instalador em todas as versões,
drivers, escalas e redes Windows. A evidência atual e as lacunas estão em `QA_REPORT.md`.
