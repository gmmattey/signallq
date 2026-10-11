export function HomeBenefits() {
  const beneficios = [
    {
      titulo: "Quedas e instabilidades",
      icone: (
        <svg className="w-6 h-6 text-[#5B21D6]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
      ),
      sintoma: "A internet cai quando você mais precisa?",
      solucao:
        "O SignallQ testa o sinal Wi-Fi, a resposta do roteador e a rota da operadora para dizer onde a conexão falhou.",
    },
    {
      titulo: "Vídeos travando e chamadas lentas",
      icone: (
        <svg className="w-6 h-6 text-[#5B21D6]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
          />
        </svg>
      ),
      sintoma: "A voz engasga mesmo com velocidade contratada?",
      solucao:
        "O app mede oscilação (jitter) e inchaço de fila (bufferbloat) sob carga, indicando se outros aparelhos estão saturando o link.",
    },
    {
      titulo: "Wi-Fi que não chega no quarto",
      icone: (
        <svg className="w-6 h-6 text-[#5B21D6]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0"
          />
        </svg>
      ),
      sintoma: "O sinal fica fraco em cômodos distantes?",
      solucao:
        "Mede a intensidade real em dBm e a interferência de canais vizinhos para orientar o melhor posicionamento do roteador.",
    },
    {
      titulo: "Prova real para a operadora",
      icone: (
        <svg className="w-6 h-6 text-[#5B21D6]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
      ),
      sintoma: "O suporte diz que está tudo normal mas não está?",
      solucao:
        "Gere relatórios em PDF com medições em múltiplos horários, sem dados pessoais expostos, para comprovar o problema.",
    },
  ];

  return (
    <section className="border-t border-[#F3EEFA] bg-white px-6 py-24">
      <div className="mx-auto max-w-[1168px]">
        <div className="max-w-[36em]">
          <div className="text-[14px] font-semibold uppercase tracking-wider text-[#5B21D6]">
            Benefícios concretos
          </div>
          <h2 className="m-0 mt-2 text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
            Diagnóstico compreensível para problemas do dia a dia.
          </h2>
          <p className="mt-3 text-pretty text-[18px] leading-7 text-[#49454F]">
            Em vez de uma tela cheia de números sem contexto, o SignallQ traduz os sinais da sua conexão em ações práticas.
          </p>
        </div>

        <div className="mt-12 grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-6">
          {beneficios.map((b) => (
            <div
              key={b.titulo}
              className="flex flex-col justify-between rounded-3xl border border-[#E7E0EC] bg-[#FAF8FC] p-7 transition-transform hover:-translate-y-1"
            >
              <div>
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white shadow-sm">
                  {b.icone}
                </div>
                <div className="mt-5 text-[20px] font-bold text-[#1C1B1F]">{b.titulo}</div>
                <div className="mt-2 text-[14px] font-semibold text-[#5B21D6]">{b.sintoma}</div>
                <p className="mt-2.5 text-[15px] leading-6 text-[#49454F]">{b.solucao}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
