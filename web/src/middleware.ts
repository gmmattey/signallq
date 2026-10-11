import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const validPaths = new Set([
  '/',
  '/privacidade',
  '/termos',
  '/como-funciona',
  '/duvidas',
  '/teste-de-velocidade',
  '/guias',
  '/funcionalidades',
  '/modo-gamer',
  '/teste-gamer',
  '/teste-de-ping',
  '/teste-de-download-e-upload',
  '/teste-de-dns',
  '/teste-de-jitter',
  '/canais-wifi',
  '/relatorio-pdf',
  '/sinal-wifi',
  '/quem-esta-na-minha-rede',
  '/mapa-de-calor-wifi',
  '/sinal-4g-5g',
]);

const legacyRedirects: Record<string, string> = {
  '/ping': '/teste-de-ping',
  '/lag-em-jogos-online': '/modo-gamer',
  '/internet-para-jogos': '/modo-gamer',
};

function isValidPath(pathname: string): boolean {
  return validPaths.has(pathname) || pathname.startsWith('/guias/');
}

export function middleware(request: NextRequest) {
  const url = request.nextUrl.clone();
  const hostname = request.nextUrl.hostname;

  // speedtest.signallq.com: domínio curto pensado pra busca/compartilhamento
  // (pedido do Luiz). Rewrite — não redirect — pra manter o domínio bonito
  // na barra de endereço; a tag canonical de /teste-de-velocidade (absoluta,
  // via routeMetadata) continua apontando pra signallq.com, então não cria
  // conteúdo duplicado para o Google.
  if (hostname === 'speedtest.signallq.com') {
    url.pathname = '/teste-de-velocidade';
    return NextResponse.rewrite(url);
  }

  if (hostname === 'signallq.pages.dev') {
    if (url.pathname === '/admin' || url.pathname.startsWith('/admin/')) {
      return NextResponse.next();
    }

    url.hostname = 'signallq.com';
    url.port = '';
    url.protocol = 'https:';

    if (legacyRedirects[url.pathname]) {
      url.pathname = legacyRedirects[url.pathname];
    } else if (!isValidPath(url.pathname)) {
      url.pathname = '/';
    }

    return NextResponse.redirect(url, 308);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/((?!api|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)',
  ],
};
