// SignallQ Design System — tokens (MD3, migração 2026-07-13; paleta violeta #5B21D6).
// LK = tema claro (default). LK_DARK = tema escuro (mesmas chaves). SPACE = grid 8dp.
// Componentes usam LK direto; aliases legados (accent/bgCard/textPrimary/border…) mantidos
// por compat. Fonte de verdade dos valores: .claude/skills/SignallQ-design/colors_and_type.css.

export const LK = {
  // ---- MD3 color roles (light) ----
  primary: '#5B21D6', onPrimary: '#FFFFFF', primaryContainer: '#EAE0FF', onPrimaryContainer: '#210A5C',
  secondary: '#2851B8', onSecondary: '#FFFFFF', secondaryContainer: '#DCE6FF', onSecondaryContainer: '#001A41',
  success: '#146C2E', onSuccess: '#FFFFFF', successContainer: '#B6F2BE', onSuccessContainer: '#04210D',
  warning: '#8A5000', onWarning: '#FFFFFF', warningContainer: '#FFDDB3', onWarningContainer: '#2B1700',
  error: '#BA1A1A', onError: '#FFFFFF', errorContainer: '#FFDAD6', onErrorContainer: '#410002',
  phaseLatencia: '#2563EB', phaseDownload: '#146C2E', phaseUpload: '#8A5000',
  background: '#FFFFFF', onBackground: '#1C1B1F',
  surface: '#FFFFFF', surfaceDim: '#DED8E1',
  surfaceContainerLowest: '#FFFFFF', surfaceContainerLow: '#F8F5FB', surfaceContainer: '#F3EEFA',
  surfaceContainerHigh: '#ECE5F5', surfaceContainerHighest: '#E6DDF2',
  onSurface: '#1C1B1F', onSurfaceVariant: '#49454F',
  outline: '#79747E', outlineVariant: '#CAC4D0',
  inverseSurface: '#313033', inverseOnSurface: '#F4EFF4',
  scrim: 'rgba(0,0,0,.5)',

  // ---- Profundidade (4 níveis — ver docs/04-design-system.md seção 6) ----
  // level0 fundo · level1 conteúdo agrupado · level2 interativo/destacado · level3 sobreposto.
  // Sombra é reforço discreto, nunca a forma principal de separação — tint de superfície vem primeiro.
  depthLevel0Tint: '#FFFFFF', depthLevel0Shadow: 'none',
  depthLevel1Tint: '#F3EEFA', depthLevel1Shadow: 'none',
  depthLevel2Tint: '#ECE5F5', depthLevel2Shadow: '0 1px 3px rgba(0,0,0,.12)',
  depthLevel3Tint: '#E6DDF2', depthLevel3Shadow: '0 4px 12px rgba(0,0,0,.20)',
  surfaceSelectedBg: '#ECE5F5', surfaceSelectedBorder: 'rgba(91,33,214,.28)', // primary @28%

  // ---- Shape (radius por componente) ----
  rCard: 16, rBtn: 20, rField: 12, rSheet: 28, rDialog: 24, rPill: 999,

  // ---- Tipografia ----
  font: "'Google Sans Flex', 'Google Sans', 'Roboto', system-ui, sans-serif",

  // ---- Aliases legados (mantidos por compat com componentes existentes) ----
  accent: '#5B21D6', accentBlue: '#2851B8',
  bgPrimary: '#FFFFFF', bgSecondary: '#F8F5FB', bgCard: '#FFFFFF',
  textPrimary: '#1C1B1F', textSecondary: '#49454F', textTertiary: '#49454F',
  border: '#79747E',
} as const;

/**
 * Forma de qualquer conjunto de tokens de tema (LK ou LK_DARK) — usada pelo ThemeProvider.
 * Preserva string/number por chave (em vez de uma união solta) para que cores continuem
 * tipadas como string e radii como number.
 */
export type TokenSet = { [K in keyof typeof LK]: (typeof LK)[K] extends number ? number : string };

export const LK_DARK: TokenSet = {
  primary: '#D0BCFF', onPrimary: '#38137E', primaryContainer: '#4F2FA8', onPrimaryContainer: '#EADDFF',
  secondary: '#AAC7FF', onSecondary: '#002E69', secondaryContainer: '#1E427A', onSecondaryContainer: '#D9E2FF',
  success: '#83DA99', onSuccess: '#00390F', successContainer: '#0A5321', onSuccessContainer: '#9DF4AC',
  warning: '#FFB870', onWarning: '#4A2900', warningContainer: '#693D00', onWarningContainer: '#FFDDB3',
  error: '#FFB4AB', onError: '#690005', errorContainer: '#93000A', onErrorContainer: '#FFDAD6',
  phaseLatencia: '#AAC7FF', phaseDownload: '#83DA99', phaseUpload: '#FFB870',
  background: '#131217', onBackground: '#E6E0E9',
  surface: '#131217', surfaceDim: '#131217',
  surfaceContainerLowest: '#0E0D12', surfaceContainerLow: '#1D1B20', surfaceContainer: '#211F26',
  surfaceContainerHigh: '#2B2930', surfaceContainerHighest: '#36343B',
  onSurface: '#E6E0E9', onSurfaceVariant: '#CAC4D0',
  outline: '#948F99', outlineVariant: '#49454F',
  inverseSurface: '#E6E0E9', inverseOnSurface: '#313033',
  scrim: 'rgba(0,0,0,.6)',

  depthLevel0Tint: '#131217', depthLevel0Shadow: 'none',
  depthLevel1Tint: '#211F26', depthLevel1Shadow: 'none',
  depthLevel2Tint: '#2B2930', depthLevel2Shadow: '0 1px 3px rgba(0,0,0,.30)',
  depthLevel3Tint: '#36343B', depthLevel3Shadow: '0 4px 12px rgba(0,0,0,.40)',
  surfaceSelectedBg: '#2B2930', surfaceSelectedBorder: 'rgba(208,188,255,.28)', // primary @28%

  rCard: 16, rBtn: 20, rField: 12, rSheet: 28, rDialog: 24, rPill: 999,
  font: "'Google Sans Flex', 'Google Sans', 'Roboto', system-ui, sans-serif",

  accent: '#D0BCFF', accentBlue: '#AAC7FF',
  bgPrimary: '#131217', bgSecondary: '#1D1B20', bgCard: '#211F26',
  textPrimary: '#E6E0E9', textSecondary: '#CAC4D0', textTertiary: '#CAC4D0',
  border: '#948F99',
};

/** Escala de espaçamento (grid 8dp, 8 degraus). */
export const SPACE = { xs: 4, sm: 8, md: 12, base: 16, lg: 20, xl: 24, xxl: 32, xxxl: 40 } as const;

/** Opacidades das state layers MD3 (aplicar sobre currentColor). */
export const STATE = { hover: 0.08, focus: 0.1, pressed: 0.12, dragged: 0.16 } as const;

/** SignallQ AI surfaces — sempre escuras, independentes de tema (DESCONTINUADA no To-Be). */
export const ORB = {
  bg: '#0D0D1A', surface: '#1A0B2E', card: '#1E1130', text: '#F3F4F6', sub: '#9CA3AF',
} as const;
