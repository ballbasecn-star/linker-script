-- Upgrade stats_json from TEXT to JSONB for queryability.
-- NULL and empty-string values are safely handled.

ALTER TABLE ls_script
    ALTER COLUMN stats_json TYPE JSONB
    USING CASE
            WHEN stats_json IS NULL OR btrim(stats_json) = '' THEN NULL
            ELSE stats_json::jsonb
          END;
