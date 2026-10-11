"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";

// O protótipo destaca a página ativa com pill roxo suave (#EAE0FF).
const LINKS: { href: string; label: string; neverActive?: boolean }[] = [
  { href: "/teste-de-velocidade", label: "Teste de velocidade", neverActive: true },
  { href: "/funcionalidades", label: "Funcionalidades" },
  { href: "/guias", label: "Guias" },
  { href: "/como-funciona", label: "Como funciona" },
  { href: "/duvidas", label: "Dúvidas" },
];

// Na home o protótipo inclui atalho para a seção "O resultado".
const HOME_LINKS: { href: string; label: string; neverActive?: boolean }[] = [
  { href: "/teste-de-velocidade", label: "Teste de velocidade" },
  { href: "/funcionalidades", label: "Funcionalidades" },
  { href: "/como-funciona", label: "Como funciona" },
  { href: "/guias", label: "Guias" },
  { href: "#resultado", label: "O resultado" },
  { href: "/duvidas", label: "Dúvidas" },
];

export function SiteNav() {
  const pathname = usePathname() ?? "";
  const links = pathname === "/" ? HOME_LINKS : LINKS;

  return (
    <header className="sticky top-0 z-10 leading-[normal] border-b border-[#F3EEFA] bg-white/[0.92] backdrop-blur-[8px]">
      <div className="mx-auto flex max-w-[1168px] items-center gap-8 px-6 py-[14px]">
        <Link
          href="/"
          aria-label="Página inicial SignallQ"
          className="flex items-center gap-[10px] text-[18px] font-bold text-[#1C1B1F] no-underline"
        >
          <Image src="/assets/signallq-symbol-512.png" alt="" width={32} height={32} className="h-8 w-8 object-contain" />
          SignallQ
        </Link>

        <nav className="ml-auto hidden items-center gap-1 text-[14px] sm:flex" aria-label="Navegação principal">
          {links.map((link) => {
            const active = !link.neverActive && !link.href.startsWith("#") && (pathname === link.href || pathname.startsWith(`${link.href}/`));
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-full px-3 py-2 no-underline transition-colors ${
                  active
                    ? "bg-[#EAE0FF] font-semibold text-[#210A5C]"
                    : "font-medium text-[#49454F] hover:text-[#210A5C]"
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>

        <Link
          href="/#baixar"
          className="ml-auto rounded-full bg-[#5B21D6] px-5 py-[11px] text-[14px] font-medium text-white no-underline sm:ml-0"
        >
          Baixar o app
        </Link>
      </div>
    </header>
  );
}
