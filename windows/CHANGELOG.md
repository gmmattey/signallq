# Changelog — SignallQ para Windows

Formato [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), versões [SemVer](https://semver.org/lang/pt-BR/).
Processo completo em [`docs/windows/RELEASE_PROCESS.md`](../docs/windows/RELEASE_PROCESS.md).

Escreva para quem usa o app, em PT-BR, direto: o que mudou e o que isso significa. Nada de nota
interna, nome de agente ou número de PR. Antes de criar a tag, mova o conteúdo de "Não lançado" para
uma seção `## [X.Y.Z] - AAAA-MM-DD`: o workflow de release usa essa seção como texto da release e
falha se ela não existir.

## [Não lançado]

### Adicionado

- Novo visual: menu em seis áreas, tema claro e escuro, fonte própria do SignallQ, animações suaves
  e telas de carregamento enquanto os dados chegam.
- Em **Minha rede**, a conexão ativa, o Wi‑Fi e a última medição ficam juntos.
- Em **Ferramentas**, o monitoramento em segundo plano agora fica junto de ping, DNS e rota.
- Gráfico de download e upload no **Histórico**, com filtro por rede.

### Alterado

- Quando um valor não foi medido, o app mostra "—" ou "Indisponível". Nunca zero.
- A mensagem de exportação concluída aparece como aviso rápido e some sozinha.

### Limitações conhecidas

- O app ainda não tem assinatura digital: o Windows pode mostrar "O Windows protegeu o seu
  computador" na instalação. Confira o SHA-256 publicado junto com o instalador.
- O Modo gamer não tem botão para cancelar a medição da rota.
