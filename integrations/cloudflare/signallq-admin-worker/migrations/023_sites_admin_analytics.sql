-- Painel Sites: dimensão de produto comprovada por credencial de ingest e
-- contexto web normalizado. Migração exclusivamente aditiva: eventos antigos
-- recebem product='unknown' e não entram nos comparativos SignallQ/Lagcheck/Linka.
--
-- Aplicar somente quando autorizado:
-- npx wrangler d1 execute signallq-admin-db --file=migrations/023_sites_admin_analytics.sql --remote
--
-- SQLite/D1 não aceita ADD COLUMN IF NOT EXISTS. Execute uma única vez; se uma
-- recuperação exigir repetição, rode os ALTERs individualmente e ignore apenas
-- o erro explícito de coluna já existente.

ALTER TABLE analytics_events ADD COLUMN product TEXT NOT NULL DEFAULT 'unknown';
ALTER TABLE analytics_events ADD COLUMN page_path TEXT NOT NULL DEFAULT '';
ALTER TABLE analytics_events ADD COLUMN cta_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_analytics_product_platform_created
  ON analytics_events(product, platform, created_at);

CREATE INDEX IF NOT EXISTS idx_analytics_product_event_created
  ON analytics_events(product, event_name, created_at);
