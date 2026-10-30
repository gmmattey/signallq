-- GH#1341: modelo de dados de avaliações completas do Google Play (Android Publisher API v3,
-- reviews.list) -- hoje handleGooglePlaySync descarta o texto e usa só starRating pra calcular
-- uma média (ver src/index.ts:2376). O épico exige nota, comentário, idioma, versão, dispositivo,
-- resposta do desenvolvedor e status de tratamento -- isso é uma lista de registros identificáveis
-- por reviewId (não estado pontual de config como admin_settings, não série temporal como
-- integration_metric_snapshots/migration 016), então precisa de tabela própria com o reviewId
-- como chave (decisão da Claudete, 2026-07-24 — registro removido de docs/archive/decisoes/ na Fase
-- 4d do épico #1623, git preserva).
--
-- UPDATE-em-lugar (não histórico de versão): a própria API não expõe histórico de edição de
-- review, só o estado atual (comentário mais recente + resposta do dev, se houver) -- preservar
-- histórico de edição não é requisito dos épicos e não teria fonte de dado real por trás.
--
-- handling_status é um campo ADMIN-side (marcado manualmente no Console, não vem da API do
-- Google) -- o sync (INSERT ... ON CONFLICT) nunca deve sobrescrevê-lo. Ver exemplo de upsert
-- no comentário abaixo da tabela.
--
-- Aplicar via: npx wrangler d1 execute signallq-admin-db --file=migrations/017_gh1341_google_play_reviews.sql --remote

CREATE TABLE IF NOT EXISTS google_play_reviews (
  review_id             TEXT    PRIMARY KEY,          -- reviewId da Android Publisher API
  rating                INTEGER NOT NULL,              -- starRating (1-5) do userComment mais recente
  comment_text          TEXT    NOT NULL DEFAULT '',   -- userComment.text
  language              TEXT    NOT NULL DEFAULT '',   -- userComment.reviewerLanguage
  device                TEXT    NOT NULL DEFAULT '',   -- userComment.device
  android_os_version    INTEGER,                       -- userComment.androidOsVersion
  app_version_code      INTEGER,                       -- userComment.appVersionCode
  app_version_name      TEXT    NOT NULL DEFAULT '',   -- userComment.appVersionName
  review_last_modified  INTEGER,                       -- Unix timestamp (segundos) -- userComment.lastModified
  developer_reply_text  TEXT    DEFAULT NULL,           -- developerComment.text (nulo = sem resposta ainda)
  developer_reply_at    INTEGER DEFAULT NULL,           -- Unix timestamp (segundos) -- developerComment.lastModified
  handling_status       TEXT    NOT NULL DEFAULT 'pending', -- pending | replied | dismissed -- só o admin altera, sync nunca sobrescreve
  first_synced_at       INTEGER NOT NULL,               -- primeira vez que essa review foi vista pelo worker
  last_synced_at        INTEGER NOT NULL                -- última vez que essa review foi vista/atualizada pelo worker
);

CREATE INDEX IF NOT EXISTS idx_google_play_reviews_rating          ON google_play_reviews(rating);
CREATE INDEX IF NOT EXISTS idx_google_play_reviews_last_synced     ON google_play_reviews(last_synced_at);
CREATE INDEX IF NOT EXISTS idx_google_play_reviews_handling_status ON google_play_reviews(handling_status);

-- Exemplo de upsert que preserva handling_status e first_synced_at (não usar INSERT OR REPLACE,
-- que zeraria os dois nos syncs seguintes):
--
-- INSERT INTO google_play_reviews (
--   review_id, rating, comment_text, language, device, android_os_version, app_version_code,
--   app_version_name, review_last_modified, developer_reply_text, developer_reply_at,
--   handling_status, first_synced_at, last_synced_at
-- ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)
-- ON CONFLICT(review_id) DO UPDATE SET
--   rating               = excluded.rating,
--   comment_text         = excluded.comment_text,
--   language             = excluded.language,
--   device               = excluded.device,
--   android_os_version   = excluded.android_os_version,
--   app_version_code     = excluded.app_version_code,
--   app_version_name     = excluded.app_version_name,
--   review_last_modified = excluded.review_last_modified,
--   developer_reply_text = excluded.developer_reply_text,
--   developer_reply_at   = excluded.developer_reply_at,
--   last_synced_at       = excluded.last_synced_at;
--   -- handling_status e first_synced_at ficam de fora do SET -- preservam o valor já gravado.
