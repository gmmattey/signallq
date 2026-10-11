import Image from "next/image";
import Link from "next/link";
import { LAGCHECK_URL, LINKA_URL } from "@/lib/config";

const LINK = "text-[#49454F] hover:text-[#210A5C] no-underline transition-colors";

export function SiteFooter() {
  return (
    <footer className="leading-[normal] border-t border-[#F3EEFA] bg-white px-6 py-10">
      <div className="mx-auto flex max-w-[1120px] flex-col gap-6 text-[14px] text-[#49454F]">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#F3EEFA] pb-6">
          <Link href="/" className="flex items-center gap-2 font-bold text-[#1C1B1F] no-underline">
            <Image src="/assets/signallq-symbol-512.png" alt="" width={24} height={24} className="h-6 w-6 object-contain" />
            SignallQ
          </Link>
          <div className="flex flex-wrap gap-x-6 gap-y-2 text-[13px]">
            <Link href="/teste-de-velocidade" className={LINK}>Teste de velocidade</Link>
            <Link href="/funcionalidades" className={LINK}>Funcionalidades</Link>
            <Link href="/guias" className={LINK}>Guias</Link>
            <a href={LAGCHECK_URL} target="_blank" rel="noopener noreferrer" className={LINK}>Lagcheck</a>
            <a href={LINKA_URL} target="_blank" rel="noopener noreferrer" className={LINK}>Linka (iOS)</a>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-x-8 gap-y-3 text-[14px]">
          <Link href="/privacidade" className={LINK}>Privacidade</Link>
          <Link href="/termos" className={LINK}>Termos</Link>
          <a href="mailto:suporte@signallq.com" className={LINK}>suporte@signallq.com</a>
          <span className="ml-auto text-[13px] text-[#79747E]">© 2026 SignallQ · Buildea</span>
        </div>
      </div>
    </footer>
  );
}

