"use client";

export function SpeedTestConnectionInfo() {
  return (
    <div className="mt-8 pt-6 border-t border-[#E7E0EC] grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs text-[#49454F] text-left">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-full bg-[#F3EEFA] flex items-center justify-center text-[#5B21D6] shrink-0">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2"
              d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7 7z"
            />
          </svg>
        </div>
        <div>
          <div className="text-[#79747E] font-medium text-[11px] uppercase tracking-wider">Conexão</div>
          <div className="text-[#1C1B1F] font-bold text-sm">Detecção Local Ativa</div>
          <div className="text-[#79747E] text-[11px]">Nenhum dado pessoal retido</div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-full bg-[#F3EEFA] flex items-center justify-center text-[#5B21D6] shrink-0">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2"
              d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9"
            />
          </svg>
        </div>
        <div>
          <div className="text-[#79747E] font-medium text-[11px] uppercase tracking-wider">Servidor de Teste</div>
          <div className="text-[#1C1B1F] font-bold text-sm flex items-center gap-1.5">
            <span>SignallQ Edge</span>
            <span className="inline-block w-2 h-2 rounded-full bg-[#146C2E]" />
          </div>
          <div className="text-[#79747E] text-[11px]">Cloudflare Multi-stream HTTP/3</div>
        </div>
      </div>
    </div>
  )
}
