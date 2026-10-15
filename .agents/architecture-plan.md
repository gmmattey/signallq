# Architecture Plan — SignallQ Windows Desktop

## Problema e comportamento esperado

Entregar um aplicativo Windows instalável que mantenha a jornada do SignallQ — entender,
diagnosticar, resolver e confirmar — com interface local fiel ao protótipo aprovado, dados medidos
de verdade e limites explícitos para capacidades indisponíveis.

## Decisão

Criar `windows/` como aplicação isolada **Tauri 2 + React/Vite + Rust**. O host Rust é dono de
coleta Windows, medições, processos controlados e persistência SQLite; a UI só consome comandos e
eventos DTO versionados. `android/` e `web/` não são acoplados nem movidos.

Electron foi rejeitado pelo runtime maior e uma superfície de execução nativa mais ampla. WinUI 3
foi rejeitado porque exigiria reescrever o protótipo HTML e reduziria a fidelidade visual.

## Fronteiras e contratos

- `windows/ui/`: apresentação, navegação e estados; não calcula diagnóstico e não invoca shell.
- `windows/src-tauri/`: adaptadores Windows, medição, histórico local e comandos fechados.
- `measurement/v1`: `executionId`, status, proveniência, confiança, capacidades e `null` para
  indisponibilidade. Ausência não é zero.
- Diagnóstico/Assist remoto só será habilitado depois de um contrato de autenticação desktop; até
  lá a UI declara indisponibilidade, sem credencial embutida ou resposta fictícia.

## Persistência e falhas

SQLite local versionado em `%LOCALAPPDATA%\\SignallQ`, separado de Room. O alvo arquitetural é
cancelamento cooperativo sem gravar resultado completo; na fundação atual ele é pendência.
Timeout, troca de rede e resultado parcial nunca viram sucesso. Bandeja e monitoramento persistente
também são pendências; sair encerra a sessão atual.

## Segurança e privacidade

Sem Node integration, segredo no frontend ou executável arbitrário. Ferramentas aceitam somente
destinos validados, binários/argumentos fixos, timeout e encerramento explícito. Inventário local
não sai do dispositivo por padrão.

## Testes e riscos

Cobrir unidades Rust/TypeScript, migrations SQLite, vetores de conformidade com os contratos
Android, E2E no runner Windows e comparação de screenshots a 100%, 125% e 150%. Windows 10/11,
WebView2, tray, DPI e instalador só serão considerados validados com evidência em Windows.

Riscos abertos: autenticação do NDS/Assist, disponibilidade desigual de APIs Wi-Fi, dependências
externas de sonda e a necessidade de não duplicar o motor Kotlin fora de um contrato testado.

## Não-objetivos desta fundação

Não importar banco Room, não alegar ping de servidor oficial de jogo, não inventar cobertura
Wi-Fi e não publicar/deployar nenhum serviço remoto.
