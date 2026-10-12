import type { Metadata, Viewport } from "next";
import Script from "next/script";
import "../index.css";
import { SiteNav } from "../components/SiteNav";
import { SiteFooter } from "../components/SiteFooter";
import { SITE_ORIGIN } from "../lib/routeMetadata";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_ORIGIN),
  title: "SignallQ — o app que descobre por que sua internet está ruim",
  description: "O app que não para no número: aponta causas prováveis da sua internet ruim. Baixe na Play Store.",
  icons: {
    icon: [
      {
        url: "/assets/signallq-favicon-web-light-bg.png",
        sizes: "1024x1024",
        type: "image/png",
        media: "(prefers-color-scheme: light)",
      },
      {
        url: "/assets/signallq-favicon-web-dark-bg.png",
        sizes: "1024x1024",
        type: "image/png",
        media: "(prefers-color-scheme: dark)",
      },
    ],
    apple: [{ url: "/assets/signallq-icon-512-play-store-dark.png", sizes: "1024x1024", type: "image/png" }],
  },
  appleWebApp: {
    capable: true,
    title: "SignallQ",
    statusBarStyle: "black-translucent",
  },
};

export const viewport: Viewport = {
  themeColor: "#5B21D6",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  // maximumScale e userScalable removidos intencionalmente para permitir zoom (Acessibilidade)
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" className="antialiased" data-sq-theme="light">
      <head>
        {/* eslint-disable-next-line @next/next/no-css-tags */}
        <link rel="stylesheet" href="/assets/google-sans-flex.css" />
        {/* eslint-disable-next-line @next/next/no-css-tags */}
        <link rel="stylesheet" href="/_ds/signallq-design-system-2d25d7a1-31b2-4ac3-881f-72dbc8f35a29/_ds_bundle.css" />
        {/* eslint-disable-next-line @next/next/no-css-tags */}
        <link rel="stylesheet" href="/_ds/signallq-design-system-2d25d7a1-31b2-4ac3-881f-72dbc8f35a29/styles.css" />
      </head>
      <body className="bg-[color:var(--bg-primary)] text-[color:var(--text-primary)]">
        {/* Vercel Analytics centraliza visitas e pageviews. A telemetria
            de uso das ferramentas permanece no pipeline de produto. */}
        <Script src="/_vercel/insights/script.js" strategy="afterInteractive" />
        <div className="flex min-h-screen w-full flex-col">
          <SiteNav />
          <main className="flex w-full flex-1 flex-col">
            {children}
          </main>
          <SiteFooter />
        </div>
      </body>
    </html>
  );
}
