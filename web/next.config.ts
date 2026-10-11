import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  turbopack: {},
  async redirects() {
    return [
      // Mantém um único host público indexável. O destino absoluto preserva
      // caminho e query string; o redirecionamento permanente é aplicado
      // somente ao host www, sem interferir nas demais rotas ou hosts.
      {
        source: '/:path*',
        has: [{ type: 'host', value: 'www.signallq.com' }],
        destination: 'https://signallq.com/:path*',
        permanent: true,
      },
      // Redirecionamentos permanentes (308) de URLs legadas com tráfego e impressões de busca
      {
        source: '/ping',
        destination: '/teste-de-ping',
        permanent: true,
      },
      {
        source: '/lag-em-jogos-online',
        destination: '/modo-gamer',
        permanent: true,
      },
      {
        source: '/internet-para-jogos',
        destination: '/modo-gamer',
        permanent: true,
      },
    ];
  },
};

export default nextConfig;
